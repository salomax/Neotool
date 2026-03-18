package processor

import (
	"bufio"
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/infrastructure/database"
	b3http "github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/infrastructure/http"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/shared"
)

// downloadFn is the function signature both annual and daily downloaders satisfy.
type downloadFn func(ctx context.Context) (zipPath string, cleanup func(), err error)

// CotahistTask downloads one COTAHIST ZIP (annual or daily) and processes it
// in two passes:
//
//  1. Pass 1 — catalog: stream all records, collect unique AssetRecords, upsert to
//     investment_asset, then bulk-SELECT ticker→UUID into an in-memory map.
//  2. Pass 2 — prices: stream all records again, look up UUID from map, upsert
//     OHLCV rows to asset_daily_price in chunks.
//
// The task is idempotent: both upserts are guarded by content_hash comparisons.
type CotahistTask struct {
	label      string     // for logging: "2026" or "20260317"
	download   downloadFn
	assetRepo  *database.AssetRepository
	priceRepo  *database.PriceRepository
	chunkSize  int
	logger     *slog.Logger
}

// NewAnnualTask creates a task that processes a full-year COTAHIST_A{year}.ZIP.
func NewAnnualTask(
	year int,
	client *b3http.B3Client,
	assetRepo *database.AssetRepository,
	priceRepo *database.PriceRepository,
	chunkSize int,
	logger *slog.Logger,
) *CotahistTask {
	return &CotahistTask{
		label: fmt.Sprintf("%d", year),
		download: func(ctx context.Context) (string, func(), error) {
			return client.DownloadAnnualToTempFile(ctx, year)
		},
		assetRepo: assetRepo,
		priceRepo: priceRepo,
		chunkSize: chunkSize,
		logger:    logger,
	}
}

// NewDailyTask creates a task that processes a single-day COTAHIST_D{DDMMYYYY}.ZIP.
func NewDailyTask(
	date time.Time,
	client *b3http.B3Client,
	assetRepo *database.AssetRepository,
	priceRepo *database.PriceRepository,
	chunkSize int,
	logger *slog.Logger,
) *CotahistTask {
	return &CotahistTask{
		label: date.Format("20060102"),
		download: func(ctx context.Context) (string, func(), error) {
			return client.DownloadDailyToTempFile(ctx, date)
		},
		assetRepo: assetRepo,
		priceRepo: priceRepo,
		chunkSize: chunkSize,
		logger:    logger,
	}
}

// newPreDownloadedTask creates a task that processes an already-downloaded ZIP.
// Used by the dispatcher pipeline: the dispatcher downloads files sequentially
// and hands the path to a process-only task, so workers never download concurrently.
func newPreDownloadedTask(
	label string,
	zipPath string,
	cleanup func(),
	assetRepo *database.AssetRepository,
	priceRepo *database.PriceRepository,
	chunkSize int,
	logger *slog.Logger,
) *CotahistTask {
	return &CotahistTask{
		label: label,
		download: func(_ context.Context) (string, func(), error) {
			return zipPath, cleanup, nil
		},
		assetRepo: assetRepo,
		priceRepo: priceRepo,
		chunkSize: chunkSize,
		logger:    logger,
	}
}

// Process implements workerpool.WorkItem.
func (t *CotahistTask) Process(ctx context.Context) error {
	startedAt := time.Now()
	t.logger.Info("cotahist task starting", "label", t.label)

	// ── Download ──────────────────────────────────────────────────────────────
	zipPath, cleanup, err := t.download(ctx)
	if err != nil {
		return fmt.Errorf("task %s: download: %w", t.label, err)
	}
	defer cleanup()

	// ── Pass 1: catalog ───────────────────────────────────────────────────────
	assets, err := t.collectAssets(zipPath)
	if err != nil {
		return fmt.Errorf("task %s: pass 1 (catalog): %w", t.label, err)
	}
	t.logger.Info("cotahist task: unique assets collected",
		"label", t.label,
		"assets", len(assets),
	)

	if len(assets) == 0 {
		t.logger.Warn("cotahist task: no qualifying records found", "label", t.label)
		return nil
	}

	if err := t.assetRepo.UpsertBatch(ctx, assets); err != nil {
		return fmt.Errorf("task %s: upserting assets: %w", t.label, err)
	}

	// Build ticker slice for the lookup query.
	tickers := make([]string, 0, len(assets))
	for _, a := range assets {
		tickers = append(tickers, a.Ticker)
	}
	tickerToID, err := t.assetRepo.FindTickerIDs(ctx, tickers)
	if err != nil {
		return fmt.Errorf("task %s: loading ticker IDs: %w", t.label, err)
	}
	t.logger.Info("cotahist task: ticker map loaded",
		"label", t.label,
		"mapped", len(tickerToID),
	)

	// ── Pass 2: prices ────────────────────────────────────────────────────────
	stats, err := t.upsertPrices(ctx, zipPath, tickerToID)
	if err != nil {
		return fmt.Errorf("task %s: pass 2 (prices): %w", t.label, err)
	}

	t.logger.Info("cotahist task completed",
		"label", t.label,
		"total_lines", stats.totalLines,
		"price_inserted", stats.inserted,
		"price_unchanged", stats.unchanged,
		"price_skipped", stats.skipped,
		"duration_ms", time.Since(startedAt).Milliseconds(),
	)
	return nil
}

// collectAssets streams the ZIP once and returns one AssetRecord per unique ticker.
// Only records matching our BDI + TPMERC filters are included.
func (t *CotahistTask) collectAssets(zipPath string) ([]domain.AssetRecord, error) {
	zr, rc, err := b3http.OpenTXTFromZip(zipPath)
	if err != nil {
		return nil, err
	}
	defer zr.Close()
	defer rc.Close()

	seen := make(map[string]domain.AssetRecord) // ticker → best record seen
	scanner := bufio.NewScanner(rc)

	for scanner.Scan() {
		rec, parseErr := domain.ParseLine(scanner.Bytes())
		if parseErr != nil || rec == nil {
			continue
		}

		assetType, ok := domain.BDIToAssetType(rec.CodBDI)
		if !ok {
			continue // BDI code not in our target set
		}
		// For equities and FIIs, restrict to spot market (options have their own TPMERC).
		if (assetType == domain.AssetTypeEquity || assetType == domain.AssetTypeFII) &&
			rec.TpMerc != domain.TpMercSpot {
			continue
		}

		if _, alreadySeen := seen[rec.Ticker]; alreadySeen {
			continue // deduplicate — catalog fields don't change within one file
		}

		// Options in COTAHIST reuse the underlying equity's CODISI (ISIN), which
		// would violate uq_investment_asset_isin_code when both the option and its
		// underlying are inserted in the same batch. Options are uniquely identified
		// by their ticker (encoding underlying + expiry + strike), so ISIN is not
		// needed and is always stored as NULL for option assets.
		isin := rec.IsinCode
		if assetType == domain.AssetTypeOptionCall || assetType == domain.AssetTypeOptionPut {
			isin = ""
		}

		ar := domain.AssetRecord{
			Ticker:    rec.Ticker,
			Name:      rec.Name,
			AssetType: assetType,
			Especi:    rec.Especi,
			IsinCode:  isin,
			LotSize:   domain.LotSizeFor(assetType),
		}
		ar.ContentHash = shared.ComputeAssetHash(ar.Ticker, ar.Name, ar.AssetType, ar.Especi)
		seen[rec.Ticker] = ar
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("scanning ZIP: %w", err)
	}

	result := make([]domain.AssetRecord, 0, len(seen))
	for _, ar := range seen {
		result = append(result, ar)
	}
	return result, nil
}

type priceStats struct {
	totalLines int
	inserted   int
	unchanged  int
	skipped    int
}

// upsertPrices streams the ZIP a second time and batch-upserts OHLCV rows.
// Rows whose tickers are not in tickerToID (catalog miss) are silently skipped.
func (t *CotahistTask) upsertPrices(
	ctx context.Context,
	zipPath string,
	tickerToID map[string]uuid.UUID,
) (priceStats, error) {
	zr, rc, err := b3http.OpenTXTFromZip(zipPath)
	if err != nil {
		return priceStats{}, err
	}
	defer zr.Close()
	defer rc.Close()

	var stats priceStats
	// chunk is allocated once and reset via [:0] after each commit — backing array reused.
	chunk := make([]domain.PriceUpsertParam, 0, t.chunkSize)
	scanner := bufio.NewScanner(rc)

	flush := func() error {
		ins, unc, batchErr := t.priceRepo.UpsertBatch(ctx, chunk)
		if batchErr != nil {
			return batchErr
		}
		stats.inserted += ins
		stats.unchanged += unc
		chunk = chunk[:0]
		return nil
	}

	for scanner.Scan() {
		stats.totalLines++

		rec, parseErr := domain.ParseLine(scanner.Bytes())
		if parseErr != nil || rec == nil {
			continue
		}

		assetType, ok := domain.BDIToAssetType(rec.CodBDI)
		if !ok {
			stats.skipped++
			continue
		}
		if (assetType == domain.AssetTypeEquity || assetType == domain.AssetTypeFII) &&
			rec.TpMerc != domain.TpMercSpot {
			stats.skipped++
			continue
		}

		assetID, ok := tickerToID[rec.Ticker]
		if !ok {
			// Should not happen after a successful catalog pass, but guard anyway.
			stats.skipped++
			continue
		}

		dateStr := rec.Date.Format("2006-01-02")
		chunk = append(chunk, domain.PriceUpsertParam{
			AssetID:     assetID,
			PriceDate:   rec.Date,
			OpenPrice:   rec.OpenPrice,
			HighPrice:   rec.HighPrice,
			LowPrice:    rec.LowPrice,
			ClosePrice:  rec.ClosePrice,
			AvgPrice:    rec.AvgPrice,
			Volume:      rec.Volume,
			Trades:      rec.Trades,
			Source:      domain.SourceB3,
			ContentHash: shared.ComputePriceHash(rec.Ticker, dateStr, rec.OpenPrice, rec.HighPrice, rec.LowPrice, rec.ClosePrice),
		})

		if len(chunk) >= t.chunkSize {
			if err := flush(); err != nil {
				return stats, fmt.Errorf("upserting price chunk: %w", err)
			}
		}
	}
	if err := scanner.Err(); err != nil {
		return stats, fmt.Errorf("scanning ZIP: %w", err)
	}

	// Flush the final partial chunk.
	if len(chunk) > 0 {
		if err := flush(); err != nil {
			return stats, fmt.Errorf("upserting final price chunk: %w", err)
		}
	}

	return stats, nil
}
