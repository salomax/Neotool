package processor

import (
	"context"
	"encoding/csv"
	"fmt"
	"io"
	"log/slog"
	"strings"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/database"
	cvmhttp "github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/http"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/shared"
	"github.com/salomax/invistus/workflow/golang/shared/workerpool"
)

// CatalogIngester streams cad_fi.csv from CVM, accumulates chunk-sized slices
// of FundRecord, and submits CatalogChunkTask items to the worker pool.
//
// Memory strategy:
//   - Never accumulates the full file in memory.
//   - After pool.Submit(task), the ingester reassigns chunk to a fresh slice so
//     the old backing array is owned exclusively by the task goroutine.
//   - existingHashes acts as a first-level filter: records whose hash has not
//     changed are skipped before task submission, reducing DB round-trips.
type CatalogIngester struct {
	client    *cvmhttp.CVMClient
	repo      *database.AssetRepository
	pool      *workerpool.WorkerPool
	chunkSize int
	logger    *slog.Logger
}

func NewCatalogIngester(
	client *cvmhttp.CVMClient,
	repo *database.AssetRepository,
	pool *workerpool.WorkerPool,
	chunkSize int,
	logger *slog.Logger,
) *CatalogIngester {
	return &CatalogIngester{
		client:    client,
		repo:      repo,
		pool:      pool,
		chunkSize: chunkSize,
		logger:    logger,
	}
}

// Run streams the fund catalog, submits chunks for upsert, and returns counts.
func (ci *CatalogIngester) Run(ctx context.Context) (total, submitted, skipped int, err error) {
	startedAt := time.Now()

	// Load existing hashes once — used to skip unchanged records.
	existingHashes, err := ci.repo.FindAllFundHashes(ctx)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("loading existing fund hashes: %w", err)
	}
	ci.logger.Info("catalog ingester: existing hashes loaded", "count", len(existingHashes))

	// Open streaming HTTP response.
	body, err := ci.client.StreamFundCatalog(ctx)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("opening catalog stream: %w", err)
	}
	defer body.Close()

	reader := csv.NewReader(body)
	reader.Comma = ';'
	reader.LazyQuotes = true
	reader.TrimLeadingSpace = true
	// ReuseRecord avoids a new []string allocation per row (~500k rows).
	reader.ReuseRecord = true

	// Read header row to map column positions.
	header, err := reader.Read()
	if err != nil {
		return 0, 0, 0, fmt.Errorf("reading catalog header: %w", err)
	}
	cols := shared.ColumnIndex(header)

	chunk := make([]domain.FundRecord, 0, ci.chunkSize)

	for {
		row, readErr := reader.Read()
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			ci.logger.Warn("catalog ingester: skipping malformed row", "error", readErr)
			continue
		}

		total++

		rec, parseErr := parseRow(row, cols)
		if parseErr != nil {
			ci.logger.Warn("catalog ingester: skipping unparseable row",
				"row", total, "error", parseErr)
			continue
		}

		// First-level filter: skip if hash matches the stored value.
		if existingHash, ok := existingHashes[rec.CNPJ]; ok && existingHash == rec.ContentHash {
			skipped++
			continue
		}

		submitted++
		chunk = append(chunk, rec)

		if len(chunk) >= ci.chunkSize {
			if submitErr := ci.submitChunk(ctx, chunk); submitErr != nil {
				return total, submitted, skipped, submitErr
			}
			// Transfer ownership to task; allocate a new backing array.
			chunk = make([]domain.FundRecord, 0, ci.chunkSize)
		}
	}

	// Flush the final partial chunk.
	if len(chunk) > 0 {
		if submitErr := ci.submitChunk(ctx, chunk); submitErr != nil {
			return total, submitted, skipped, submitErr
		}
	}

	ci.logger.Info("catalog ingester: streaming complete",
		"total_rows", total,
		"submitted", submitted,
		"skipped_unchanged", skipped,
		"duration_ms", time.Since(startedAt).Milliseconds(),
	)
	return total, submitted, skipped, nil
}

func (ci *CatalogIngester) submitChunk(ctx context.Context, chunk []domain.FundRecord) error {
	task := NewCatalogChunkTask(chunk, ci.repo, ci.logger)
	if err := ci.pool.Submit(ctx, task); err != nil {
		return fmt.Errorf("submitting catalog chunk: %w", err)
	}
	return nil
}

// parseRow converts one CSV row into a FundRecord.
// Column names are as published by CVM in cad_fi.csv.
func parseRow(row []string, cols map[string]int) (domain.FundRecord, error) {
	cnpj := shared.NormalizeCNPJ(shared.GetField(row, cols, "CNPJ_FUNDO"))
	if cnpj == "" {
		return domain.FundRecord{}, fmt.Errorf("empty CNPJ")
	}

	name := shared.GetField(row, cols, "DENOM_SOCIAL")
	classe := shared.GetField(row, cols, "CLASSE")
	tipoFundo := shared.GetField(row, cols, "TP_FUNDO")
	manager := shared.GetField(row, cols, "GESTOR")
	situacao := shared.GetField(row, cols, "SITUACAO")

	isActive := strings.EqualFold(situacao, "EM FUNCIONAMENTO NORMAL")
	assetType := resolveAssetType(tipoFundo, classe)
	contentHash := shared.ComputeFundHash(cnpj, name, classe, tipoFundo, manager, isActive)

	return domain.FundRecord{
		CNPJ:        cnpj,
		Name:        name,
		Classe:      classe,
		TipoFundo:   tipoFundo,
		Manager:     manager,
		IsActive:    isActive,
		AssetType:   assetType,
		ContentHash: contentHash,
	}, nil
}

// resolveAssetType maps CVM tipo_fundo / classe to our canonical asset type string.
func resolveAssetType(tipoFundo, classe string) string {
	switch strings.ToUpper(strings.TrimSpace(tipoFundo)) {
	case "PGBL":
		return "PGBL"
	case "VGBL":
		return "VGBL"
	}
	cl := strings.ToUpper(strings.TrimSpace(classe))
	switch {
	case strings.Contains(cl, "RENDA FIXA"):
		return "FIXED_INCOME_FUND"
	case strings.Contains(cl, "AÇÕES") || strings.Contains(cl, "ACOES"):
		return "EQUITY_FUND"
	case strings.Contains(cl, "MULTIMERCADO"):
		return "MULTI_MARKET_FUND"
	case strings.Contains(cl, "CAMBIAL"):
		return "FOREX_FUND"
	default:
		return "MUTUAL_FUND"
	}
}
