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
//	Submit one task per year from (currentYear − backfillYears) through currentYear.
//
// Incremental strategy (last_data_date present):
//
//   - If last_data_date is in a prior year: submit tasks for remaining years up
//     to currentYear (handles resume after a multi-year backfill that was interrupted).
//   - If last_data_date is in the current year: submit a single task for the
//     current year only. Annual files are updated by B3 after each trading session,
//     so re-downloading them nightly is cheap (~30 MB) and content-hash guards
//     skip all unchanged rows.
//
// Download pipeline:
//
//	A dedicated goroutine downloads files one at a time (sequential) and sends the
//	zip path to the worker pool via a buffered channel. Workers consume process-only
//	tasks (pass 1 catalog + pass 2 prices) in parallel, so DB writes are concurrent
//	while downloads are never simultaneous. This eliminates the Cloudflare 520 errors
//	caused by parallel HTTP connections to B3's CDN.
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

// zipEntry carries a successfully downloaded ZIP and its cleanup function.
type zipEntry struct {
	year    int
	zipPath string
	cleanup func()
}

// Run loads the job control state, builds the year list, then drives the
// download→process pipeline:
//
//  1. A background goroutine downloads annual ZIPs sequentially, sending each
//     to zipCh once ready.
//  2. The main goroutine receives entries from zipCh and submits process-only
//     tasks to the worker pool (pool.Submit blocks when the queue is full,
//     providing natural back-pressure on the downloader).
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

	d.logger.Info("dispatcher: starting download pipeline",
		"run_type", runType,
		"years", len(years),
		"first", years[0],
		"last", years[len(years)-1],
	)

	// Buffer 1: the downloader can fetch one file ahead of what the dispatcher
	// has submitted to the pool, hiding download latency during task transitions.
	zipCh := make(chan zipEntry, 1)
	downloadErrCh := make(chan error, 1)

	go func() {
		defer close(zipCh)
		for _, year := range years {
			d.logger.Info("dispatcher: downloading annual file", "year", year)
			zipPath, cleanup, dlErr := d.client.DownloadAnnualToTempFile(ctx, year)
			if dlErr != nil {
				select {
				case downloadErrCh <- fmt.Errorf("dispatcher: download year %d: %w", year, dlErr):
				default:
				}
				return
			}
			select {
			case zipCh <- zipEntry{year: year, zipPath: zipPath, cleanup: cleanup}:
			case <-ctx.Done():
				cleanup()
				return
			}
		}
	}()

	var submitErr error
	for entry := range zipCh {
		label := fmt.Sprintf("%d", entry.year)
		task := newPreDownloadedTask(label, entry.zipPath, entry.cleanup,
			d.assetRepo, d.priceRepo, d.chunkSize, d.logger)
		if err := d.pool.Submit(ctx, task); err != nil {
			entry.cleanup()
			submitErr = fmt.Errorf("dispatcher: submitting year %d: %w", entry.year, err)
			break
		}
	}

	// Drain the channel so the download goroutine can exit and temp files are cleaned up.
	for entry := range zipCh {
		entry.cleanup()
	}

	if submitErr != nil {
		return submitErr
	}

	// Check whether the download goroutine encountered a fatal error.
	select {
	case dlErr := <-downloadErrCh:
		return dlErr
	default:
	}

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
