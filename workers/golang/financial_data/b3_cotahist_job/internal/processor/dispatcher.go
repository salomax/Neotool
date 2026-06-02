package processor

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/infrastructure/database"
	b3http "github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/infrastructure/http"
)

// taskKind distinguishes the two file types used by this job.
type taskKind int

const (
	kindAnnual taskKind = iota // COTAHIST_A{YYYY}.ZIP
	kindDaily                  // COTAHIST_D{DDMMYYYY}.ZIP
)

// taskSpec describes one download unit before the file is fetched.
type taskSpec struct {
	kind taskKind
	year int
	date time.Time // kindDaily only
}

func (s taskSpec) label() string {
	if s.kind == kindAnnual {
		return fmt.Sprintf("%d", s.year)
	}
	return s.date.Format("20060102")
}

// Dispatcher builds the list of files to process and drives the sequential
// download → parallel-process pipeline.
//
// # File selection strategy
//
//	┌─────────────────┬──────────────────────────────────────┐
//	│  Previous years │         Current year                 │
//	│  Annual ZIPs    │  Daily ZIPs (Jan 1 → yesterday)      │
//	└─────────────────┴──────────────────────────────────────┘
//
// Short-gap optimisation: when the missing window is ≤ gapThresholdDays (default 3),
// only daily files are fetched — avoids re-downloading a large annual ZIP for a
// weekend catch-up.
//
// Monthly files are never used: annual ZIPs already carry the most recent data for
// completed years, and daily ZIPs cover the current year day-by-day.
//
// # Download discipline
//
// Downloads are strictly sequential: the next download starts only after the
// previous file has been handed off to the worker pool. The rate limiter inside
// B3Client enforces a minimum gap between browser downloads. Workers process chunks
// of the current file fully in parallel while the next download is pending.
type Dispatcher struct {
	client           *b3http.B3Client
	assetRepo        *database.AssetRepository
	priceRepo        *database.PriceRepository
	jobControlRepo   *database.JobControlRepository
	chunkSize        int
	backfillYears    int
	gapThresholdDays int
	logger           *slog.Logger
}

func NewDispatcher(
	client *b3http.B3Client,
	assetRepo *database.AssetRepository,
	priceRepo *database.PriceRepository,
	jobControlRepo *database.JobControlRepository,
	chunkSize int,
	backfillYears int,
	gapThresholdDays int,
	logger *slog.Logger,
) *Dispatcher {
	return &Dispatcher{
		client:           client,
		assetRepo:        assetRepo,
		priceRepo:        priceRepo,
		jobControlRepo:   jobControlRepo,
		chunkSize:        chunkSize,
		backfillYears:    backfillYears,
		gapThresholdDays: gapThresholdDays,
		logger:           logger,
	}
}

// Run loads job control state, builds the task list, then for each file:
// downloads it (blocking, with rate-limiter delay), submits it to the worker
// pool for processing, then moves on to the next download.
//
// Downloads are never concurrent. Workers process whichever tasks have already
// been submitted while the next download is in progress.
func (d *Dispatcher) Run(ctx context.Context) error {
	now := time.Now().UTC()

	lastDataDate, err := d.jobControlRepo.FindLastDataDate(ctx, domain.JobName)
	if err != nil {
		return fmt.Errorf("dispatcher: reading job control: %w", err)
	}

	specs, runType := d.buildTaskSpecs(lastDataDate, now)
	if len(specs) == 0 {
		d.logger.Warn("dispatcher: no files to process")
		return nil
	}

	d.logger.Info("dispatcher: starting",
		"run_type", runType,
		"tasks", len(specs),
		"first", specs[0].label(),
		"last", specs[len(specs)-1].label(),
	)

	for i, spec := range specs {
		d.logger.Info("dispatcher: downloading file",
			"file", spec.label(),
			"progress", fmt.Sprintf("%d/%d", i+1, len(specs)),
		)

		zipPath, cleanup, dlErr := d.downloadSpec(ctx, spec)
		if dlErr != nil {
			if errors.Is(dlErr, b3http.ErrFileNotFound) {
				d.logger.Info("dispatcher: no file for date, skipping (weekend/holiday)",
					"file", spec.label(),
				)
				continue
			}
			return fmt.Errorf("dispatcher: download %s: %w", spec.label(), dlErr)
		}

		// Log file size so the user can gauge how much data was fetched.
		if info, statErr := os.Stat(zipPath); statErr == nil {
			d.logger.Info("dispatcher: download complete",
				"file", spec.label(),
				"size_mb", fmt.Sprintf("%.1f", float64(info.Size())/1024/1024),
			)
		}

		task := newPreDownloadedTask(
			spec.label(),
			zipPath,
			cleanup,
			d.assetRepo,
			d.priceRepo,
			d.chunkSize,
			d.logger,
		)
		lastDate, processErr := task.Process(ctx)
		if processErr != nil {
			return fmt.Errorf("dispatcher: processing %s: %w", spec.label(), processErr)
		}

		if !lastDate.IsZero() {
			if err := d.jobControlRepo.MarkSuccess(ctx, domain.JobName, lastDate); err != nil {
				d.logger.Warn("dispatcher: failed to mark success after file", "file", spec.label(), "error", err)
			}
		}
	}

	return nil
}

// downloadSpec dispatches to the right B3Client method based on task kind.
func (d *Dispatcher) downloadSpec(ctx context.Context, spec taskSpec) (string, func(), error) {
	if spec.kind == kindAnnual {
		return d.client.DownloadAnnualToTempFile(ctx, spec.year)
	}
	return d.client.DownloadDailyToTempFile(ctx, spec.date)
}

// buildTaskSpecs returns the ordered list of files to download and the run type.
func (d *Dispatcher) buildTaskSpecs(lastDataDate *time.Time, now time.Time) ([]taskSpec, domain.RunType) {
	currentYear := now.Year()
	yesterday := now.AddDate(0, 0, -1).Truncate(24 * time.Hour)

	var startDate time.Time
	if lastDataDate == nil {
		startDate = time.Date(currentYear-d.backfillYears, time.January, 1, 0, 0, 0, 0, time.UTC)
	} else {
		startDate = lastDataDate.AddDate(0, 0, 1)
	}

	// Short-gap optimisation: small gap → daily files only.
	if lastDataDate != nil {
		gapDays := int(yesterday.Sub(*lastDataDate).Hours() / 24)
		if gapDays <= d.gapThresholdDays {
			var specs []taskSpec
			for d := startDate; !d.After(yesterday); d = d.AddDate(0, 0, 1) {
				specs = append(specs, taskSpec{kind: kindDaily, date: d})
			}
			return specs, domain.RunTypeIncremental
		}
	}

	var specs []taskSpec

	// Annual ZIPs from the start year through the current year (inclusive).
	// B3 publishes a rolling annual file for the current year that already
	// contains the most recent trading day, so no daily files are needed
	// in the backfill path.
	for y := startDate.Year(); y <= currentYear; y++ {
		specs = append(specs, taskSpec{kind: kindAnnual, year: y})
	}

	return specs, domain.RunTypeBackfill
}

// ErrFileNotFound re-exports the sentinel so main can inspect task errors.
var ErrFileNotFound = b3http.ErrFileNotFound

// IsFileNotFound returns true when the error wraps ErrFileNotFound.
func IsFileNotFound(err error) bool {
	return errors.Is(err, b3http.ErrFileNotFound)
}
