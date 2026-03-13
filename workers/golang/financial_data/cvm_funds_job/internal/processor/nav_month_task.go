package processor

import (
	"context"
	"encoding/csv"
	"fmt"
	"io"
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/database"
	cvmhttp "github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/http"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/shared"
)

// cad_fi column names used for NAV parsing.
const (
	navColCNPJ         = "CNPJ_FUNDO"
	navColDate         = "DT_COMPTC"
	navColNavPerQuota  = "VL_QUOTA"
	navColNetAssets    = "VL_TOTAL"
	navColShareholders = "NR_COTST"
)

// NAVMonthTask downloads and processes one month's inf_diario_fi_{YYYYMM}.zip.
//
// Memory strategy:
//   - ZIP is written to a temp file (archive/zip requires seekable io.ReaderAt).
//   - Within the task, rows are accumulated into a chunk slice.
//   - After each batch commit, chunk is reset via chunk[:0] to reuse the
//     backing array — avoiding repeated allocations inside the hot loop.
type NAVMonthTask struct {
	yearMonth domain.YearMonth
	assetIDs  map[string]uuid.UUID // cnpj → asset UUID (read-only, shared across tasks)
	client    *cvmhttp.CVMClient
	repo      *database.NavRepository
	chunkSize int
	logger    *slog.Logger
}

func NewNAVMonthTask(
	yearMonth domain.YearMonth,
	assetIDs map[string]uuid.UUID,
	client *cvmhttp.CVMClient,
	repo *database.NavRepository,
	chunkSize int,
	logger *slog.Logger,
) *NAVMonthTask {
	return &NAVMonthTask{
		yearMonth: yearMonth,
		assetIDs:  assetIDs,
		client:    client,
		repo:      repo,
		chunkSize: chunkSize,
		logger:    logger,
	}
}

// Process downloads the monthly ZIP, streams the CSV, and commits rows in chunks.
func (t *NAVMonthTask) Process(ctx context.Context) error {
	startedAt := time.Now()
	ym := t.yearMonth.String()

	// Download ZIP to a temp file — archive/zip.OpenReader requires io.ReaderAt.
	zipPath, cleanup, err := t.client.DownloadNavToTempFile(ctx, ym)
	if err != nil {
		return fmt.Errorf("nav %s: downloading ZIP: %w", ym, err)
	}
	defer cleanup()

	// Open the first CSV inside the ZIP.
	zr, csvRC, err := cvmhttp.OpenFirstCSVFromZip(zipPath)
	if err != nil {
		return fmt.Errorf("nav %s: opening CSV in ZIP: %w", ym, err)
	}
	defer zr.Close()
	defer csvRC.Close()

	reader := csv.NewReader(csvRC)
	reader.Comma = ';'
	reader.LazyQuotes = true
	reader.TrimLeadingSpace = true
	// ReuseRecord avoids per-row []string allocation (millions of rows per full backfill).
	reader.ReuseRecord = true

	header, err := reader.Read()
	if err != nil {
		return fmt.Errorf("nav %s: reading CSV header: %w", ym, err)
	}
	cols := shared.ColumnIndex(header)

	var (
		totalRows int
		inserted  int
		unchanged int
		skipped   int
	)

	// chunk is allocated once and reset via [:0] after each commit — backing
	// array is reused, so no re-allocation inside the hot loop.
	chunk := make([]domain.NavUpsertParam, 0, t.chunkSize)

	for {
		row, readErr := reader.Read()
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			t.logger.Warn("nav task: skipping malformed row", "month", ym, "error", readErr)
			continue
		}

		totalRows++

		param, parseErr := t.parseNavRow(row, cols)
		if parseErr != nil {
			skipped++
			continue
		}

		chunk = append(chunk, param)

		if len(chunk) >= t.chunkSize {
			ins, unc, commitErr := t.repo.UpsertNavBatch(ctx, chunk)
			if commitErr != nil {
				return fmt.Errorf("nav %s: upserting chunk: %w", ym, commitErr)
			}
			inserted += ins
			unchanged += unc
			// Reuse backing array — avoids re-allocation in the hot loop.
			chunk = chunk[:0]
		}
	}

	// Flush the final partial chunk.
	if len(chunk) > 0 {
		ins, unc, commitErr := t.repo.UpsertNavBatch(ctx, chunk)
		if commitErr != nil {
			return fmt.Errorf("nav %s: upserting final chunk: %w", ym, commitErr)
		}
		inserted += ins
		unchanged += unc
	}

	t.logger.Info("nav task completed",
		"month", ym,
		"total_rows", totalRows,
		"inserted", inserted,
		"unchanged", unchanged,
		"skipped", skipped,
		"duration_ms", time.Since(startedAt).Milliseconds(),
	)
	return nil
}

// parseNavRow converts one CSV row from inf_diario_fi into a NavUpsertParam.
// Returns an error when required fields are missing or the fund is not in the catalog.
func (t *NAVMonthTask) parseNavRow(row []string, cols map[string]int) (domain.NavUpsertParam, error) {
	cnpj := shared.NormalizeCNPJ(shared.GetField(row, cols, navColCNPJ))
	if cnpj == "" {
		return domain.NavUpsertParam{}, fmt.Errorf("empty CNPJ")
	}

	assetID, ok := t.assetIDs[cnpj]
	if !ok {
		// Fund not in catalog (unlisted / inactive) — skip silently.
		return domain.NavUpsertParam{}, fmt.Errorf("fund not in catalog: %s", cnpj)
	}

	rawDate := shared.GetField(row, cols, navColDate)
	priceDate, err := time.Parse("2006-01-02", rawDate)
	if err != nil {
		return domain.NavUpsertParam{}, fmt.Errorf("invalid date %q: %w", rawDate, err)
	}

	navStr := shared.GetField(row, cols, navColNavPerQuota)
	navPerQuota, err := strconv.ParseFloat(strings.Replace(navStr, ",", ".", 1), 64)
	if err != nil {
		return domain.NavUpsertParam{}, fmt.Errorf("invalid VL_QUOTA %q: %w", navStr, err)
	}

	netAssetsStr := shared.GetField(row, cols, navColNetAssets)
	netAssets, _ := strconv.ParseFloat(strings.Replace(netAssetsStr, ",", ".", 1), 64)

	var shareholders *int
	if shStr := shared.GetField(row, cols, navColShareholders); shStr != "" {
		if sh, parseErr := strconv.Atoi(shStr); parseErr == nil {
			shareholders = &sh
		}
	}

	return domain.NavUpsertParam{
		AssetID:        assetID,
		PriceDate:      priceDate,
		ClosePrice:     navPerQuota,
		TotalNetAssets: netAssets,
		Shareholders:   shareholders,
		Source:         domain.SourceCVM,
		ContentHash:    shared.ComputeNavHash(cnpj, rawDate, navPerQuota),
	}, nil
}
