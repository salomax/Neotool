package processor

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/database"
	cvmhttp "github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/http"
	"github.com/salomax/invistus/workflow/golang/shared/workerpool"
)

// NAVDispatcher builds the list of months to process and submits one NAVMonthTask
// per month to the worker pool.
//
// On first run (lastDataDate == nil) it backfills up to backfillYears of history.
// On subsequent runs it processes only months since the last successful import date.
type NAVDispatcher struct {
	client         *cvmhttp.CVMClient
	assetRepo      *database.AssetRepository
	navRepo        *database.NavRepository
	jobControlRepo *database.JobControlRepository
	pool           *workerpool.WorkerPool
	chunkSize      int
	backfillYears  int
	logger         *slog.Logger

	// lastDay is set by Run and read by main after pool.Wait().
	lastDay *time.Time
}

// LastDay returns the last day of the processed window, or nil if Run has not
// been called or produced no months. Used by main to call MarkSuccess only
// after all workers have completed.
func (d *NAVDispatcher) LastDay() *time.Time { return d.lastDay }

func NewNAVDispatcher(
	client *cvmhttp.CVMClient,
	assetRepo *database.AssetRepository,
	navRepo *database.NavRepository,
	jobControlRepo *database.JobControlRepository,
	pool *workerpool.WorkerPool,
	chunkSize int,
	backfillYears int,
	logger *slog.Logger,
) *NAVDispatcher {
	return &NAVDispatcher{
		client:         client,
		assetRepo:      assetRepo,
		navRepo:        navRepo,
		jobControlRepo: jobControlRepo,
		pool:           pool,
		chunkSize:      chunkSize,
		backfillYears:  backfillYears,
		logger:         logger,
	}
}

// Run loads the asset catalog map, determines which months to process, and
// submits one NAVMonthTask per month to the worker pool.
func (d *NAVDispatcher) Run(ctx context.Context) error {
	startedAt := time.Now()

	// Load fund catalog once — shared read-only by all month tasks.
	assetIDs, err := d.assetRepo.FindAllFundIDs(ctx)
	if err != nil {
		return fmt.Errorf("nav dispatcher: loading fund IDs: %w", err)
	}
	d.logger.Info("nav dispatcher: fund catalog loaded", "funds", len(assetIDs))

	if len(assetIDs) == 0 {
		d.logger.Warn("nav dispatcher: no funds in catalog — run catalog job first")
		return nil
	}

	// Determine the run window.
	lastDataDate, err := d.jobControlRepo.FindLastDataDate(ctx, domain.JobNameNAV)
	if err != nil {
		return fmt.Errorf("nav dispatcher: reading job control: %w", err)
	}

	months := buildMonthList(lastDataDate, d.backfillYears)
	if len(months) == 0 {
		d.logger.Warn("nav dispatcher: no months to process — last_data_date may be in the future",
			"last_data_date", lastDataDate)
		return nil
	}

	runType := domain.RunTypeIncremental
	if lastDataDate == nil {
		runType = domain.RunTypeBackfill
	}

	d.logger.Info("nav dispatcher: submitting months",
		"run_type", runType,
		"months", len(months),
		"first", months[0].String(),
		"last", months[len(months)-1].String(),
	)

	// Submit one task per month — pool controls concurrency.
	for _, ym := range months {
		task := NewNAVMonthTask(ym, assetIDs, d.client, d.navRepo, d.chunkSize, d.logger)
		if err := d.pool.Submit(ctx, task); err != nil {
			return fmt.Errorf("nav dispatcher: submitting month %s: %w", ym, err)
		}
	}

	// Store lastDay for main to call MarkSuccess after pool.Wait().
	lastDay := months[len(months)-1].LastDay()
	d.lastDay = &lastDay

	d.logger.Info("nav dispatcher: all months submitted",
		"duration_ms", time.Since(startedAt).Milliseconds(),
	)
	return nil
}

// buildMonthList returns the ordered list of YearMonth values to process.
//
//   - If lastDataDate is nil (first run): backfill backfillYears × 12 months back.
//   - Otherwise: start from the month of lastDataDate and process up to current month.
func buildMonthList(lastDataDate *time.Time, backfillYears int) []domain.YearMonth {
	now := time.Now().UTC()
	// Include the current month: CVM publishes inf_diario_fi_{YYYYMM}.zip mid-month
	// and updates it daily, so current-month data is available before month-end.
	lastMonth := domain.NewYearMonth(now.Year(), now.Month())

	var startMonth domain.YearMonth
	if lastDataDate == nil {
		startMonth = lastMonth.AddMonths(-backfillYears * 12)
	} else {
		startMonth = domain.NewYearMonth(lastDataDate.Year(), lastDataDate.Month())
	}

	var months []domain.YearMonth
	for ym := startMonth; !ym.After(lastMonth); ym = ym.AddMonths(1) {
		months = append(months, ym)
	}
	return months
}
