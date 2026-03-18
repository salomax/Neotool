package processor

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/infrastructure/database"
	b3http "github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/infrastructure/http"
	"github.com/salomax/invistus/workflow/golang/shared/workerpool"
)

// Dispatcher determines which COTAHIST files need processing and submits tasks to the pool.
//
// Backfill strategy (no last_data_date):
//
//	Submit one YearTask per year from (currentYear − backfillYears) through currentYear.
//
// Incremental strategy (last_data_date present):
//
//   - If last_data_date is in a prior year: submit YearTasks for remaining years up
//     to currentYear (handles resume after a multi-year backfill that was interrupted).
//   - If last_data_date is in the current year: submit a single YearTask for the
//     current year only. Annual files are updated by B3 after each trading session,
//     so re-downloading them nightly is cheap (~30 MB) and content-hash guards
//     skip all unchanged rows.
//
// MarkSuccess is called by main after pool.Wait() — the dispatcher stores the
// target lastDate during Run() so main can call it only once all workers succeed.
type Dispatcher struct {
	client         *b3http.B3Client
	assetRepo      *database.AssetRepository
	priceRepo      *database.PriceRepository
	jobControlRepo *database.JobControlRepository
	pool           *workerpool.WorkerPool
	chunkSize      int
	backfillYears  int
	logger         *slog.Logger

	// lastDate is set by Run and read by main after pool.Wait().
	lastDate *time.Time
}

// LastDate returns the target last_data_date to pass to MarkSuccess, or nil if
// Run produced no tasks. Must be called after pool.Wait().
func (d *Dispatcher) LastDate() *time.Time { return d.lastDate }

func NewDispatcher(
	client *b3http.B3Client,
	assetRepo *database.AssetRepository,
	priceRepo *database.PriceRepository,
	jobControlRepo *database.JobControlRepository,
	pool *workerpool.WorkerPool,
	chunkSize int,
	backfillYears int,
	logger *slog.Logger,
) *Dispatcher {
	return &Dispatcher{
		client:         client,
		assetRepo:      assetRepo,
		priceRepo:      priceRepo,
		jobControlRepo: jobControlRepo,
		pool:           pool,
		chunkSize:      chunkSize,
		backfillYears:  backfillYears,
		logger:         logger,
	}
}

// Run loads the job control state, builds the year list, and submits one
// CotahistTask per year to the worker pool.
func (d *Dispatcher) Run(ctx context.Context) error {
	now := time.Now().UTC()
	currentYear := now.Year()
	// yesterday is used as last_data_date after a successful run. B3 annual files
	// contain data up to the previous trading day (D+1 publication), so yesterday
	// is a safe upper bound. Non-trading days (weekends / holidays) are fine —
	// the next run simply re-processes the current year's file cheaply.
	yesterday := now.AddDate(0, 0, -1).Truncate(24 * time.Hour)

	lastDataDate, err := d.jobControlRepo.FindLastDataDate(ctx, domain.JobName)
	if err != nil {
		return fmt.Errorf("dispatcher: reading job control: %w", err)
	}

	years, runType := buildYearList(lastDataDate, currentYear, d.backfillYears)
	if len(years) == 0 {
		d.logger.Warn("dispatcher: no years to process")
		return nil
	}

	d.logger.Info("dispatcher: submitting year tasks",
		"run_type", runType,
		"years", len(years),
		"first", years[0],
		"last", years[len(years)-1],
	)

	for _, year := range years {
		task := NewAnnualTask(year, d.client, d.assetRepo, d.priceRepo, d.chunkSize, d.logger)
		if err := d.pool.Submit(ctx, task); err != nil {
			return fmt.Errorf("dispatcher: submitting year %d: %w", year, err)
		}
	}

	// Store target lastDate for main to call MarkSuccess after pool.Wait().
	d.lastDate = &yesterday
	return nil
}

// buildYearList returns the ordered list of years to process and the run type.
func buildYearList(lastDataDate *time.Time, currentYear, backfillYears int) ([]int, domain.RunType) {
	var startYear int
	var runType domain.RunType

	if lastDataDate == nil {
		// First run: backfill N years of history.
		startYear = currentYear - backfillYears
		runType = domain.RunTypeBackfill
	} else if lastDataDate.Year() < currentYear {
		// Interrupted backfill or year boundary: resume from the last processed year.
		startYear = lastDataDate.Year()
		runType = domain.RunTypeBackfill
	} else {
		// Incremental: only current year's annual file needs re-processing.
		startYear = currentYear
		runType = domain.RunTypeIncremental
	}

	years := make([]int, 0, currentYear-startYear+1)
	for y := startYear; y <= currentYear; y++ {
		years = append(years, y)
	}
	return years, runType
}

// ErrFileNotFound re-exports the sentinel so main can inspect task errors.
var ErrFileNotFound = b3http.ErrFileNotFound

// IsFileNotFound returns true when the error wraps ErrFileNotFound.
// Used by the pool error handler to distinguish skippable 404s from real failures.
func IsFileNotFound(err error) bool {
	return errors.Is(err, b3http.ErrFileNotFound)
}
