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

// CotahistTask downloads one COTAHIST ZIP (annual, monthly, or daily) and
// processes it in two passes:
//
//  1. Pass 1 — catalog: stream all records, collect unique AssetRecords, upsert to
//     investment_asset in chunks, then bulk-SELECT ticker→UUID into memory.
//  2. Pass 2 — prices: stream all records again, look up UUID, upsert OHLCV rows
//     to asset_daily_price in chunks. Each chunk runs in its own BEGIN/COMMIT
//     transaction, limiting lock scope and making deadlock retries cheap.
//
// Both upserts are guarded by content_hash comparisons (idempotent re-runs).
type CotahistTask struct {
	label     string     // for logging: "2026", "202602", "20260302"
	download  downloadFn
	assetRepo *database.AssetRepository
	priceRepo *database.PriceRepository
	chunkSize int
	logger    *slog.Logger
}

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

// newPreDownloadedTask creates a process-only task from an already-downloaded ZIP.
// Used by the dispatcher pipeline: downloads are sequential, processing is parallel.
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

// Process runs the full ETL for one COTAHIST file and returns the most recent
// price date found in the file (used by the dispatcher to update job_control).
func (t *CotahistTask) Process(ctx context.Context) (time.Time, error) {
	startedAt := time.Now()
	t.logger.Info("cotahist task starting", "label", t.label)

	// ── Download ──────────────────────────────────────────────────────────────
	zipPath, cleanup, err := t.download(ctx)
	if err != nil {
		return time.Time{}, fmt.Errorf("task %s: download: %w", t.label, err)
	}
	defer cleanup() // always remove the temp file when done

	// ── Pass 1: catalog ───────────────────────────────────────────────────────
	assets, err := t.collectAssets(zipPath)
	if err != nil {
		return time.Time{}, fmt.Errorf("task %s: pass 1 (catalog): %w", t.label, err)
	}
	t.logger.Info("cotahist task: unique assets collected",
		"label", t.label,
		"assets", len(assets),
	)

	if len(assets) == 0 {
		t.logger.Warn("cotahist task: no qualifying records found", "label", t.label)
		return time.Time{}, nil
	}

	// Upsert assets in chunks — each chunk is one BEGIN/COMMIT transaction,
	// keeping lock scope small and deadlock retries cheap.
	totalAssetChunks := (len(assets) + t.chunkSize - 1) / t.chunkSize
	for i := 0; i < len(assets); i += t.chunkSize {
		end := min(i+t.chunkSize, len(assets))
		chunkIdx := i/t.chunkSize + 1
		if err := t.assetRepo.UpsertBatch(ctx, assets[i:end]); err != nil {
			return time.Time{}, fmt.Errorf("task %s: upserting assets chunk %d/%d: %w", t.label, chunkIdx, totalAssetChunks, err)
		}
		t.logger.Info("cotahist task: asset chunk committed",
			"label", t.label,
			"chunk", fmt.Sprintf("%d/%d", chunkIdx, totalAssetChunks),
			"records", end-i,
		)
	}

	// Build ticker slice for the lookup query.
	tickers := make([]string, 0, len(assets))
	for _, a := range assets {
		tickers = append(tickers, a.Ticker)
	}
	tickerToID, err := t.assetRepo.FindTickerIDs(ctx, tickers)
	if err != nil {
		return time.Time{}, fmt.Errorf("task %s: loading ticker IDs: %w", t.label, err)
	}
	t.logger.Info("cotahist task: ticker map loaded",
		"label", t.label,
		"mapped", len(tickerToID),
	)

	// ── Pass 2: prices ────────────────────────────────────────────────────────
	stats, err := t.upsertPrices(ctx, zipPath, tickerToID)
	if err != nil {
		return time.Time{}, fmt.Errorf("task %s: pass 2 (prices): %w", t.label, err)
	}

	t.logger.Info("cotahist task completed",
		"label", t.label,
		"total_lines", stats.totalLines,
		"price_inserted", stats.inserted,
		"price_unchanged", stats.unchanged,
		"price_skipped", stats.skipped,
		"last_date", stats.lastDate.Format("2006-01-02"),
		"duration_ms", time.Since(startedAt).Milliseconds(),
	)
	return stats.lastDate, nil
}

// collectAssets streams the ZIP once and returns one AssetRecord per unique ticker.
func (t *CotahistTask) collectAssets(zipPath string) ([]domain.AssetRecord, error) {
	zr, rc, err := b3http.OpenTXTFromZip(zipPath)
	if err != nil {
		return nil, err
	}
	defer zr.Close()
	defer rc.Close()

	seen := make(map[string]domain.AssetRecord)
	scanner := bufio.NewScanner(rc)

	for scanner.Scan() {
		rec, parseErr := domain.ParseLine(scanner.Bytes())
		if parseErr != nil || rec == nil {
			continue
		}

		assetType, ok := domain.BDIToAssetType(rec.CodBDI)
		if !ok {
			continue
		}
		if (assetType == domain.AssetTypeEquity || assetType == domain.AssetTypeFII) &&
			rec.TpMerc != domain.TpMercSpot {
			continue
		}

		if _, alreadySeen := seen[rec.Ticker]; alreadySeen {
			continue
		}

		// Options in COTAHIST reuse the underlying equity's ISIN, which would
		// violate uq_investment_asset_isin_code. Options are identified by ticker
		// (underlying + expiry + strike), so ISIN is stored as NULL for them.
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
	lastDate   time.Time // most recent price_date seen in the file
}

// upsertPrices streams the ZIP a second time and batch-upserts OHLCV rows.
// Each chunk of chunkSize rows is committed in its own transaction.
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
	chunk := make([]domain.PriceUpsertParam, 0, t.chunkSize)
	scanner := bufio.NewScanner(rc)
	chunkIdx := 0

	flush := func() error {
		ins, unc, batchErr := t.priceRepo.UpsertBatch(ctx, chunk)
		if batchErr != nil {
			return batchErr
		}
		stats.inserted += ins
		stats.unchanged += unc
		chunkIdx++
		// Log progress every 10 chunks (~10 000 rows).
		if chunkIdx%10 == 0 {
			t.logger.Info("cotahist task: price progress",
				"label", t.label,
				"chunks_committed", chunkIdx,
				"inserted", stats.inserted,
				"unchanged", stats.unchanged,
			)
		}
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
			stats.skipped++
			continue
		}

		if rec.Date.After(stats.lastDate) {
			stats.lastDate = rec.Date
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

	if len(chunk) > 0 {
		if err := flush(); err != nil {
			return stats, fmt.Errorf("upserting final price chunk: %w", err)
		}
	}

	return stats, nil
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
