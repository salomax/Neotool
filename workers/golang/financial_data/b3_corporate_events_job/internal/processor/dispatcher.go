package processor

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/infrastructure/database"
	b3http "github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/infrastructure/http"
	"github.com/salomax/invistus/workflow/golang/shared/workerpool"
)

// Dispatcher paginates through B3's company list and submits one CompanyTask per company.
type Dispatcher struct {
	b3Client     *b3http.B3Client
	assetRepo    *database.AssetLookupRepository
	dividendRepo *database.CashDividendRepository
	stockRepo    *database.StockEventRepository
	pool         *workerpool.WorkerPool
	pageSize     int
	requestDelay time.Duration
	logger       *slog.Logger
}

func NewDispatcher(
	b3Client *b3http.B3Client,
	assetRepo *database.AssetLookupRepository,
	dividendRepo *database.CashDividendRepository,
	stockRepo *database.StockEventRepository,
	pool *workerpool.WorkerPool,
	pageSize int,
	requestDelayMs int,
	logger *slog.Logger,
) *Dispatcher {
	return &Dispatcher{
		b3Client:     b3Client,
		assetRepo:    assetRepo,
		dividendRepo: dividendRepo,
		stockRepo:    stockRepo,
		pool:         pool,
		pageSize:     pageSize,
		requestDelay: time.Duration(requestDelayMs) * time.Millisecond,
		logger:       logger,
	}
}

// Run loads the ISIN → asset UUID map, paginates through all listed companies,
// and submits one CompanyTask per company to the worker pool.
func (d *Dispatcher) Run(ctx context.Context) error {
	startedAt := time.Now()

	// Load the full isin_code → asset_id map once; shared read-only by all tasks.
	isinToAssetID, err := d.assetRepo.FindAllByIsinCode(ctx)
	if err != nil {
		return fmt.Errorf("dispatcher: loading ISIN→asset map: %w", err)
	}
	d.logger.Info("dispatcher: ISIN map loaded", "assets", len(isinToAssetID))

	// Paginate through B3's company list.
	totalSubmitted := 0
	page := 1

	for {
		resp, err := d.b3Client.GetListedCompanies(ctx, page, d.pageSize)
		if err != nil {
			return fmt.Errorf("dispatcher: fetching companies page %d: %w", page, err)
		}

		if len(resp.Results) == 0 {
			break
		}

		for _, company := range resp.Results {
			// Skip inactive companies — they won't have recent events.
			if company.Status != "A" {
				continue
			}

			task := NewCompanyTask(
				company,
				isinToAssetID,
				d.b3Client,
				d.dividendRepo,
				d.stockRepo,
				d.requestDelay,
				d.logger,
			)
			if err := d.pool.Submit(ctx, task); err != nil {
				return fmt.Errorf("dispatcher: submitting task for %s: %w", company.IssuingCompany, err)
			}
			totalSubmitted++
		}

		d.logger.Info("dispatcher: page fetched",
			"page", page,
			"companies_on_page", len(resp.Results),
			"total_submitted", totalSubmitted,
		)

		// Stop when we've processed all pages.
		if page >= resp.Page.TotalPages || resp.Page.TotalPages <= 0 {
			break
		}
		page++
	}

	d.logger.Info("dispatcher: all companies submitted",
		"total_submitted", totalSubmitted,
		"duration_ms", time.Since(startedAt).Milliseconds(),
	)
	return nil
}
