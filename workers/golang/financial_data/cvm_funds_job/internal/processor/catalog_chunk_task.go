package processor

import (
	"context"
	"log/slog"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/database"
)

// CatalogChunkTask is a WorkItem that upserts one chunk of fund catalog records.
// Ownership of the records slice is transferred on Submit — the ingester must not
// reference it after calling pool.Submit(task).
type CatalogChunkTask struct {
	records []domain.FundRecord
	repo    *database.AssetRepository
	logger  *slog.Logger
}

func NewCatalogChunkTask(
	records []domain.FundRecord,
	repo *database.AssetRepository,
	logger *slog.Logger,
) *CatalogChunkTask {
	return &CatalogChunkTask{
		records: records,
		repo:    repo,
		logger:  logger,
	}
}

// Process upserts the chunk and explicitly releases the backing array for GC.
func (t *CatalogChunkTask) Process(ctx context.Context) error {
	// Always nil the slice on exit so the GC can reclaim the backing array.
	defer func() { t.records = nil }()

	// startedAt is set here, not at construction, so duration reflects actual
	// processing time rather than queue-wait + processing time.
	startedAt := time.Now()

	if err := t.repo.UpsertFundBatch(ctx, t.records); err != nil {
		t.logger.Error("catalog chunk upsert failed",
			"records", len(t.records),
			"error", err,
		)
		return err
	}

	t.logger.Debug("catalog chunk upserted",
		"records", len(t.records),
		"duration_ms", time.Since(startedAt).Milliseconds(),
	)
	return nil
}
