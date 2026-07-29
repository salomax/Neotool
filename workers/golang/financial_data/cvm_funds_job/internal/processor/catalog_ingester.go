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
	"golang.org/x/text/encoding/charmap"
)

// catalogColMap maps logical field names to the actual CSV column name for a given format.
type catalogColMap struct {
	cnpj                  string
	name                  string
	classe                string
	tipo                  string
	manager               string
	situacao              string
	tributacaoLongoPrazo  string
	publicoAlvo           string
	patrimonioLiquido     string
	dataPatrimonioLiquido string
	cnpjAuditor           string
	auditor               string
	cnpjCustodiante       string
	custodiante           string
}

// Legacy format (cad_fi.csv): funds registered before RCVM 175.
var legacyCols = catalogColMap{
	cnpj:     "CNPJ_FUNDO",
	name:     "DENOM_SOCIAL",
	classe:   "CLASSE",
	tipo:     "TP_FUNDO",
	manager:  "GESTOR",
	situacao: "SITUACAO",
}

// New format (registro_fundo_classe.zip): funds registered under RCVM 175 (post-2024).
var rcvm175Cols = catalogColMap{
	cnpj:                  "CNPJ_Classe",
	name:                  "Denominacao_Social",
	classe:                "Classificacao_Anbima",
	tipo:                  "Tipo_Classe",
	situacao:              "Situacao",
	tributacaoLongoPrazo:  "Tributacao_Longo_Prazo",
	publicoAlvo:           "Publico_Alvo",
	patrimonioLiquido:     "Patrimonio_Liquido",
	dataPatrimonioLiquido: "Data_Patrimonio_Liquido",
	cnpjAuditor:           "CNPJ_Auditor",
	auditor:               "Auditor",
	cnpjCustodiante:       "CNPJ_Custodiante",
	custodiante:           "Custodiante",
}

const (
	rcvm175ClasseEntryName      = "registro_classe.csv"
	rcvm175FundoEntryName       = "registro_fundo.csv"
	rcvm175RegistroFundoIDCol   = "ID_Registro_Fundo"
	rcvm175ManagerSourceColName = "Gestor"
	rcvm175AdminSourceColName   = "Administrador"
)

type registroFundoJoinData struct {
	Gestor        string
	Administrador string
}

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

// ingestState accumulates streaming counters and the in-progress chunk across sources.
type ingestState struct {
	total     int
	submitted int
	skipped   int
	chunk     []domain.FundRecord
}

// Run streams both fund catalog sources, submits chunks for upsert, and returns counts.
//
// Sources:
//  1. cad_fi.csv — legacy format, funds registered before RCVM 175.
//  2. registro_fundo_classe.zip — new format (post-2024, RCVM 175), different column names.
func (ci *CatalogIngester) Run(ctx context.Context) (total, submitted, skipped int, err error) {
	startedAt := time.Now()

	// Load existing hashes once — used to skip unchanged records across both sources.
	existingHashes, err := ci.repo.FindAllFundHashes(ctx)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("loading existing fund hashes: %w", err)
	}
	ci.logger.Info("catalog ingester: existing hashes loaded", "count", len(existingHashes))

	state := &ingestState{chunk: make([]domain.FundRecord, 0, ci.chunkSize)}

	// ── Source 1: cad_fi.csv (legacy format) ────────────────────────────────
	body, err := ci.client.StreamFundCatalog(ctx)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("opening catalog stream: %w", err)
	}
	defer body.Close()

	reader := csv.NewReader(charmap.ISO8859_1.NewDecoder().Reader(body))
	reader.Comma = ';'
	reader.LazyQuotes = true
	reader.TrimLeadingSpace = true
	reader.ReuseRecord = true

	header, err := reader.Read()
	if err != nil {
		return 0, 0, 0, fmt.Errorf("reading catalog header: %w", err)
	}

	if err = ci.streamRows(ctx, reader, shared.ColumnIndex(header), legacyCols, existingHashes, state, nil); err != nil {
		return state.total, state.submitted, state.skipped, err
	}

	// ── Source 2: registro_fundo_classe.zip (RCVM 175 new format) ───────────
	zipPath, cleanup, err := ci.client.DownloadFundClasseCatalogToTempFile(ctx)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("downloading fund classe catalog: %w", err)
	}
	defer cleanup()

	zrFund, fundCSVRC, err := cvmhttp.OpenNamedCSVFromZip(zipPath, rcvm175FundoEntryName)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("opening fund catalog %s: %w", rcvm175FundoEntryName, err)
	}
	defer zrFund.Close()
	defer fundCSVRC.Close()

	fundReader := csv.NewReader(charmap.ISO8859_1.NewDecoder().Reader(fundCSVRC))
	fundReader.Comma = ';'
	fundReader.LazyQuotes = true
	fundReader.TrimLeadingSpace = true
	fundReader.ReuseRecord = true

	fundHeader, err := fundReader.Read()
	if err != nil {
		return 0, 0, 0, fmt.Errorf("reading fund catalog header: %w", err)
	}

	// registro_fundo.csv is materially smaller than registro_classe.csv, so loading
	// the join map keeps the large class-level stream single-pass.
	managerByRegistroFundo, err := buildManagerByRegistroFundo(fundReader, shared.ColumnIndex(fundHeader))
	if err != nil {
		return 0, 0, 0, fmt.Errorf("building manager join map: %w", err)
	}

	zrClasse, classeCSVRC, err := cvmhttp.OpenNamedCSVFromZip(zipPath, rcvm175ClasseEntryName)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("opening fund classe CSV %s: %w", rcvm175ClasseEntryName, err)
	}
	defer zrClasse.Close()
	defer classeCSVRC.Close()

	reader2 := csv.NewReader(charmap.ISO8859_1.NewDecoder().Reader(classeCSVRC))
	reader2.Comma = ';'
	reader2.LazyQuotes = true
	reader2.TrimLeadingSpace = true
	reader2.ReuseRecord = true

	header2, err := reader2.Read()
	if err != nil {
		return 0, 0, 0, fmt.Errorf("reading fund classe catalog header: %w", err)
	}

	if err = ci.streamRows(
		ctx,
		reader2,
		shared.ColumnIndex(header2),
		rcvm175Cols,
		existingHashes,
		state,
		managerFromRegistroFundoJoin(managerByRegistroFundo),
	); err != nil {
		return state.total, state.submitted, state.skipped, err
	}

	// Flush the final partial chunk.
	if len(state.chunk) > 0 {
		if submitErr := ci.submitChunk(ctx, state.chunk); submitErr != nil {
			return state.total, state.submitted, state.skipped, submitErr
		}
	}

	ci.logger.Info("catalog ingester: streaming complete",
		"total_rows", state.total,
		"submitted", state.submitted,
		"skipped_unchanged", state.skipped,
		"duration_ms", time.Since(startedAt).Milliseconds(),
	)
	return state.total, state.submitted, state.skipped, nil
}

// streamRows reads all rows from reader, parses each with the given column map, and
// accumulates changed records into state.chunk — flushing to the pool when full.
func (ci *CatalogIngester) streamRows(
	ctx context.Context,
	reader *csv.Reader,
	cols map[string]int,
	colMap catalogColMap,
	existingHashes map[string]string,
	state *ingestState,
	joinResolver func(row []string, cols map[string]int) registroFundoJoinData,
) error {
	for {
		row, readErr := reader.Read()
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			ci.logger.Warn("catalog ingester: skipping malformed row", "error", readErr)
			continue
		}

		state.total++

		rec, parseErr := parseFundRow(row, cols, colMap, joinResolver)
		if parseErr != nil {
			ci.logger.Warn("catalog ingester: skipping unparseable row",
				"row", state.total, "error", parseErr)
			continue
		}

		if existingHash, ok := existingHashes[rec.CNPJ]; ok && existingHash == rec.ContentHash {
			state.skipped++
			continue
		}

		state.submitted++
		state.chunk = append(state.chunk, rec)

		if len(state.chunk) >= ci.chunkSize {
			if submitErr := ci.submitChunk(ctx, state.chunk); submitErr != nil {
				return submitErr
			}
			state.chunk = make([]domain.FundRecord, 0, ci.chunkSize)
		}
	}
	return nil
}

func buildManagerByRegistroFundo(reader *csv.Reader, cols map[string]int) (map[string]registroFundoJoinData, error) {
	managerByRegistroFundo := make(map[string]registroFundoJoinData)

	for {
		row, err := reader.Read()
		if err == io.EOF {
			return managerByRegistroFundo, nil
		}
		if err != nil {
			return nil, fmt.Errorf("reading %s row: %w", rcvm175FundoEntryName, err)
		}

		registroFundoID := shared.GetField(row, cols, rcvm175RegistroFundoIDCol)
		if registroFundoID == "" {
			continue
		}

		managerByRegistroFundo[registroFundoID] = registroFundoJoinData{
			Gestor:        shared.GetField(row, cols, rcvm175ManagerSourceColName),
			Administrador: shared.GetField(row, cols, rcvm175AdminSourceColName),
		}
	}
}

func managerFromRegistroFundoJoin(
	managerByRegistroFundo map[string]registroFundoJoinData,
) func(row []string, cols map[string]int) registroFundoJoinData {
	return func(row []string, cols map[string]int) registroFundoJoinData {
		registroFundoID := shared.GetField(row, cols, rcvm175RegistroFundoIDCol)
		if registroFundoID == "" {
			return registroFundoJoinData{}
		}
		return managerByRegistroFundo[registroFundoID]
	}
}

func (ci *CatalogIngester) submitChunk(ctx context.Context, chunk []domain.FundRecord) error {
	task := NewCatalogChunkTask(chunk, ci.repo, ci.logger)
	if err := ci.pool.Submit(ctx, task); err != nil {
		return fmt.Errorf("submitting catalog chunk: %w", err)
	}
	return nil
}

// parseFundRow converts one CSV row into a FundRecord using the provided column map.
// colMap abstracts the column name differences between the legacy and RCVM 175 formats.
func parseFundRow(
	row []string,
	cols map[string]int,
	colMap catalogColMap,
	joinResolver func(row []string, cols map[string]int) registroFundoJoinData,
) (domain.FundRecord, error) {
	cnpj := shared.NormalizeCNPJ(shared.GetField(row, cols, colMap.cnpj))
	if cnpj == "" {
		return domain.FundRecord{}, fmt.Errorf("empty CNPJ")
	}

	name := shared.GetField(row, cols, colMap.name)
	classe := shared.GetField(row, cols, colMap.classe)
	tipoFundo := shared.GetField(row, cols, colMap.tipo)
	manager := ""
	administrador := ""
	if joinResolver != nil {
		joinData := joinResolver(row, cols)
		manager = joinData.Gestor
		administrador = joinData.Administrador
	} else {
		manager = shared.GetField(row, cols, colMap.manager)
	}
	tributacaoLongoPrazo := shared.GetField(row, cols, colMap.tributacaoLongoPrazo)
	publicoAlvo := shared.GetField(row, cols, colMap.publicoAlvo)
	patrimonioLiquido := shared.GetField(row, cols, colMap.patrimonioLiquido)
	dataPatrimonioLiquido := shared.GetField(row, cols, colMap.dataPatrimonioLiquido)
	cnpjAuditor := shared.GetField(row, cols, colMap.cnpjAuditor)
	auditor := shared.GetField(row, cols, colMap.auditor)
	cnpjCustodiante := shared.GetField(row, cols, colMap.cnpjCustodiante)
	custodiante := shared.GetField(row, cols, colMap.custodiante)
	situacao := shared.GetField(row, cols, colMap.situacao)

	isActive := strings.EqualFold(strings.TrimSpace(situacao), "em funcionamento normal")
	assetType := resolveAssetType(tipoFundo, classe)
	contentHash := shared.ComputeFundHash(
		cnpj,
		name,
		classe,
		tipoFundo,
		manager,
		administrador,
		tributacaoLongoPrazo,
		publicoAlvo,
		patrimonioLiquido,
		dataPatrimonioLiquido,
		cnpjAuditor,
		auditor,
		cnpjCustodiante,
		custodiante,
		isActive,
	)

	return domain.FundRecord{
		CNPJ:                  cnpj,
		Name:                  name,
		Classe:                classe,
		TipoFundo:             tipoFundo,
		Manager:               manager,
		Administrador:         administrador,
		TributacaoLongoPrazo:  tributacaoLongoPrazo,
		PublicoAlvo:           publicoAlvo,
		PatrimonioLiquido:     patrimonioLiquido,
		DataPatrimonioLiquido: dataPatrimonioLiquido,
		CNPJAuditor:           cnpjAuditor,
		Auditor:               auditor,
		CNPJCustodiante:       cnpjCustodiante,
		Custodiante:           custodiante,
		IsActive:              isActive,
		AssetType:             assetType,
		ContentHash:           contentHash,
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
