package processor

import (
	"context"
	"fmt"
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/infrastructure/database"
	b3http "github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/infrastructure/http"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/shared"
)

// CompanyTask fetches and persists all corporate events for a single company.
// It implements workerpool.WorkItem.
type CompanyTask struct {
	company       domain.B3Company
	isinToAssetID map[string]uuid.UUID // shared read-only map (pre-loaded by dispatcher)
	b3Client      *b3http.B3Client
	dividendRepo  *database.CashDividendRepository
	stockRepo     *database.StockEventRepository
	requestDelay  time.Duration
	logger        *slog.Logger
}

func NewCompanyTask(
	company domain.B3Company,
	isinToAssetID map[string]uuid.UUID,
	b3Client *b3http.B3Client,
	dividendRepo *database.CashDividendRepository,
	stockRepo *database.StockEventRepository,
	requestDelay time.Duration,
	logger *slog.Logger,
) *CompanyTask {
	return &CompanyTask{
		company:       company,
		isinToAssetID: isinToAssetID,
		b3Client:      b3Client,
		dividendRepo:  dividendRepo,
		stockRepo:     stockRepo,
		requestDelay:  requestDelay,
		logger:        logger,
	}
}

// Process fetches B3 supplement data for the company and upserts all events to the DB.
func (t *CompanyTask) Process(ctx context.Context) error {
	// Throttle: respect the configured inter-request delay to avoid hammering B3's API.
	if t.requestDelay > 0 {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(t.requestDelay):
		}
	}

	items, err := t.b3Client.GetCompanyEvents(ctx, t.company.CodeCVM, t.company.IssuingCompany)
	if err != nil {
		return fmt.Errorf("company %s (%s): %w", t.company.IssuingCompany, t.company.CodeCVM, err)
	}
	if len(items) == 0 {
		return nil
	}

	item := items[0]

	var cashDividends []domain.CashDividendUpsertParam
	var stockEvents []domain.StockEventUpsertParam

	// ── Cash dividends ───────────────────────────────────────────────────────
	for _, cd := range item.CashDividends {
		param, err := t.parseCashDividend(cd)
		if err != nil {
			t.logger.Warn("skipping cash dividend",
				"company", t.company.IssuingCompany,
				"isin", cd.IsinCode,
				"label", cd.Label,
				"error", err,
			)
			continue
		}
		cashDividends = append(cashDividends, param)
	}

	// ── Stock dividends (splits, bonuses) ────────────────────────────────────
	for _, sd := range item.StockDividends {
		param, err := t.parseStockDividend(sd)
		if err != nil {
			t.logger.Warn("skipping stock dividend",
				"company", t.company.IssuingCompany,
				"isin", sd.IsinCode,
				"label", sd.Label,
				"error", err,
			)
			continue
		}
		stockEvents = append(stockEvents, param)
	}

	// ── Subscriptions ────────────────────────────────────────────────────────
	for _, sub := range item.Subscriptions {
		param, err := t.parseSubscription(sub)
		if err != nil {
			t.logger.Warn("skipping subscription",
				"company", t.company.IssuingCompany,
				"isin", sub.IsinCode,
				"error", err,
			)
			continue
		}
		stockEvents = append(stockEvents, param)
	}

	if err := t.dividendRepo.UpsertBatch(ctx, cashDividends); err != nil {
		return fmt.Errorf("company %s: upserting cash dividends: %w", t.company.IssuingCompany, err)
	}
	if err := t.stockRepo.UpsertBatch(ctx, stockEvents); err != nil {
		return fmt.Errorf("company %s: upserting stock events: %w", t.company.IssuingCompany, err)
	}

	t.logger.Debug("company processed",
		"company", t.company.IssuingCompany,
		"cash_dividends", len(cashDividends),
		"stock_events", len(stockEvents),
	)
	return nil
}

func (t *CompanyTask) parseCashDividend(cd domain.B3CashDividend) (domain.CashDividendUpsertParam, error) {
	dividendType, ok := domain.B3LabelToDividendType[strings.ToUpper(strings.TrimSpace(cd.Label))]
	if !ok {
		return domain.CashDividendUpsertParam{}, fmt.Errorf("unknown label %q", cd.Label)
	}

	rate, err := parseBRDecimal(cd.Rate)
	if err != nil || rate == nil {
		return domain.CashDividendUpsertParam{}, fmt.Errorf("invalid rate %q: %w", cd.Rate, err)
	}

	lastDatePrior, err := parseBRDate(cd.LastDatePrior)
	if err != nil || lastDatePrior == nil {
		return domain.CashDividendUpsertParam{}, fmt.Errorf("invalid lastDatePrior %q", cd.LastDatePrior)
	}
	paymentDate, err := parseBRDate(cd.PaymentDate)
	if err != nil || paymentDate == nil {
		return domain.CashDividendUpsertParam{}, fmt.Errorf("invalid paymentDate %q", cd.PaymentDate)
	}
	approvedOn, err := parseBRDate(cd.ApprovedOn)
	if err != nil || approvedOn == nil {
		return domain.CashDividendUpsertParam{}, fmt.Errorf("invalid approvedOn %q", cd.ApprovedOn)
	}

	isin := strings.TrimSpace(cd.IsinCode)
	var assetID *uuid.UUID
	if id, ok := t.isinToAssetID[isin]; ok {
		assetID = &id
	}

	return domain.CashDividendUpsertParam{
		AssetID:       assetID,
		IsinCode:      isin,
		DividendType:  dividendType,
		Rate:          *rate,
		LastDatePrior: *lastDatePrior,
		PaymentDate:   *paymentDate,
		ApprovedOn:    *approvedOn,
		RelatedTo:     strings.TrimSpace(cd.RelatedTo),
		Remarks:       strings.TrimSpace(cd.Remarks),
		ContentHash: shared.ComputeCashDividendHash(
			isin, dividendType,
			lastDatePrior.Format("2006-01-02"),
			paymentDate.Format("2006-01-02"),
			approvedOn.Format("2006-01-02"),
			*rate,
		),
	}, nil
}

func (t *CompanyTask) parseStockDividend(sd domain.B3StockDividend) (domain.StockEventUpsertParam, error) {
	eventType, ok := domain.B3LabelToEventType[strings.ToUpper(strings.TrimSpace(sd.Label))]
	if !ok {
		return domain.StockEventUpsertParam{}, fmt.Errorf("unknown label %q", sd.Label)
	}

	factor, err := parseBRDecimal(sd.Factor)
	if err != nil {
		return domain.StockEventUpsertParam{}, fmt.Errorf("invalid factor %q: %w", sd.Factor, err)
	}

	lastDatePrior, err := parseBRDate(sd.LastDatePrior)
	if err != nil || lastDatePrior == nil {
		return domain.StockEventUpsertParam{}, fmt.Errorf("invalid lastDatePrior %q", sd.LastDatePrior)
	}
	approvedOn, err := parseBRDate(sd.ApprovedOn)
	if err != nil || approvedOn == nil {
		return domain.StockEventUpsertParam{}, fmt.Errorf("invalid approvedOn %q", sd.ApprovedOn)
	}

	isin := strings.TrimSpace(sd.IsinCode)
	var assetID *uuid.UUID
	if id, ok := t.isinToAssetID[isin]; ok {
		assetID = &id
	}

	return domain.StockEventUpsertParam{
		AssetID:       assetID,
		IsinCode:      isin,
		EventType:     eventType,
		Factor:        factor,
		LastDatePrior: *lastDatePrior,
		ApprovedOn:    *approvedOn,
		Remarks:       strings.TrimSpace(sd.Remarks),
		ContentHash: shared.ComputeStockEventHash(
			isin, eventType,
			lastDatePrior.Format("2006-01-02"),
			approvedOn.Format("2006-01-02"),
			factor,
		),
	}, nil
}

func (t *CompanyTask) parseSubscription(sub domain.B3Subscription) (domain.StockEventUpsertParam, error) {
	percentage, err := parseBRDecimal(sub.Percentage)
	if err != nil {
		return domain.StockEventUpsertParam{}, fmt.Errorf("invalid percentage %q: %w", sub.Percentage, err)
	}

	priceUnit, err := parseBRDecimal(sub.PriceUnit)
	if err != nil {
		return domain.StockEventUpsertParam{}, fmt.Errorf("invalid priceUnit %q: %w", sub.PriceUnit, err)
	}

	lastDatePrior, err := parseBRDate(sub.LastDatePrior)
	if err != nil || lastDatePrior == nil {
		return domain.StockEventUpsertParam{}, fmt.Errorf("invalid lastDatePrior %q", sub.LastDatePrior)
	}
	approvedOn, err := parseBRDate(sub.ApprovedOn)
	if err != nil || approvedOn == nil {
		return domain.StockEventUpsertParam{}, fmt.Errorf("invalid approvedOn %q", sub.ApprovedOn)
	}

	tradingStart, tradingEnd := parseTradingPeriod(sub.TradingPeriod)
	subscriptionDate, _ := parseBRDate(sub.SubscriptionDate)

	isin := strings.TrimSpace(sub.IsinCode)
	var assetID *uuid.UUID
	if id, ok := t.isinToAssetID[isin]; ok {
		assetID = &id
	}

	return domain.StockEventUpsertParam{
		AssetID:          assetID,
		IsinCode:         isin,
		EventType:        domain.EventTypeSubscription,
		Factor:           percentage,
		PriceUnit:        priceUnit,
		TradingStart:     tradingStart,
		TradingEnd:       tradingEnd,
		SubscriptionDate: subscriptionDate,
		LastDatePrior:    *lastDatePrior,
		ApprovedOn:       *approvedOn,
		Remarks:          strings.TrimSpace(sub.Remarks),
		ContentHash: shared.ComputeStockEventHash(
			isin, domain.EventTypeSubscription,
			lastDatePrior.Format("2006-01-02"),
			approvedOn.Format("2006-01-02"),
			percentage,
		),
	}, nil
}

// parseBRDate parses a date in Brazilian format "DD/MM/YYYY".
// Returns nil for empty strings or sentinel dates (year >= 9000 or year == 1900).
func parseBRDate(s string) (*time.Time, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	t, err := time.Parse("02/01/2006", s)
	if err != nil {
		return nil, err
	}
	// Sentinel dates: B3 uses "31/12/9999" for unknown future dates
	// and some entries have "01/01/1900" as placeholder.
	if t.Year() >= 9000 || t.Year() == 1900 {
		return nil, nil
	}
	return &t, nil
}

// parseBRDecimal parses a decimal number in Brazilian format (comma as decimal separator).
// Returns nil for empty strings.
func parseBRDecimal(s string) (*float64, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	// Brazilian format uses comma as decimal separator; no thousands separator in B3 API values.
	s = strings.Replace(s, ",", ".", 1)
	v, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return nil, err
	}
	return &v, nil
}

// parseTradingPeriod splits "DD/MM/YYYY a DD/MM/YYYY" into start and end dates.
func parseTradingPeriod(s string) (start, end *time.Time) {
	parts := strings.Split(s, " a ")
	if len(parts) != 2 {
		return nil, nil
	}
	start, _ = parseBRDate(strings.TrimSpace(parts[0]))
	end, _ = parseBRDate(strings.TrimSpace(parts[1]))
	return
}
