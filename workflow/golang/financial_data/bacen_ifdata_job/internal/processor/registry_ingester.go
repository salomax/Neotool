package processor

import (
	"context"
	"fmt"
	"log/slog"
	"strings"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/bacen"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
	"github.com/invistus/bacen-ifdata/internal/shared"
)

// RegistryIngester ingests institution registry with change detection
type RegistryIngester struct {
	bacenClient     *bacen.Client
	institutionRepo *database.InstitutionRepository
	logger          *slog.Logger
}

// NewRegistryIngester creates a new registry ingester
func NewRegistryIngester(
	bacenClient *bacen.Client,
	institutionRepo *database.InstitutionRepository,
	logger *slog.Logger,
) *RegistryIngester {
	return &RegistryIngester{
		bacenClient:     bacenClient,
		institutionRepo: institutionRepo,
		logger:          logger,
	}
}

// IngestRegistry ingests institution registry for a quarter with change detection
// Processes data in chunks and commits after each page
func (ri *RegistryIngester) IngestRegistry(
	ctx context.Context,
	anoMes string,
	forceRefresh bool,
) ([]domain.InstitutionChange, error) {
	ri.logger.Info("ingesting institution registry",
		"ano_mes", anoMes,
		"force_refresh", forceRefresh)

	// Check if registry already exists (unless force refresh)
	if !forceRefresh {
		count, err := ri.institutionRepo.CountByAnoMes(ctx, anoMes)
		if err != nil {
			return nil, fmt.Errorf("failed to check existing registry: %w", err)
		}
		if count > 0 {
			ri.logger.Info("registry already exists, skipping ingestion",
				"ano_mes", anoMes,
				"existing_count", count)
			return []domain.InstitutionChange{}, nil
		}
	}

	// Track all changes across pages
	var allChanges []domain.InstitutionChange
	var colMap map[string]int
	pageCount := 0

	// Process registry page by page, committing after each page
	err := ri.bacenClient.FetchRegistryPages(ctx, anoMes, func(header []string, rows [][]string) error {
		pageCount++
		ri.logger.Info("processing registry page",
			"page", pageCount,
			"rows", len(rows),
			"ano_mes", anoMes)

		// Build column index map on first page
		if colMap == nil {
			colMap = ri.buildColumnMap(header)
		}

		// Parse and process institutions in this page
		var pageInstitutions []*domain.Institution
		var pageChanges []domain.InstitutionChange

		for _, row := range rows {
			if len(row) < len(header) {
				ri.logger.Warn("insufficient fields in registry row",
					"field_count", len(row),
					"expected", len(header),
					"page", pageCount)
				continue
			}

			// Parse institution from CSV row
			inst, err := ri.parseInstitution(row, colMap, anoMes)
			if err != nil {
				ri.logger.Warn("failed to parse institution",
					"error", err,
					"page", pageCount)
				continue
			}

			// Detect change type
			changeType, err := ri.detectChange(ctx, inst)
			if err != nil {
				return fmt.Errorf("failed to detect change for %s: %w", inst.BacenCodInst, err)
			}

			// Track change
			pageChanges = append(pageChanges, domain.InstitutionChange{
				Institution: *inst,
				ChangeType:  changeType,
			})

			// Only collect institutions that need to be upserted
			if changeType != domain.ChangeTypeUnchanged {
				pageInstitutions = append(pageInstitutions, inst)
			}
		}

		// Batch upsert changed institutions for this page
		if len(pageInstitutions) > 0 {
			if err := ri.institutionRepo.BatchUpsert(ctx, pageInstitutions); err != nil {
				return fmt.Errorf("failed to batch upsert page %d: %w", pageCount, err)
			}
			ri.logger.Info("committed page to database",
				"page", pageCount,
				"institutions_upserted", len(pageInstitutions),
				"total_changes", len(pageChanges))
		} else {
			ri.logger.Info("page processed, no changes to commit",
				"page", pageCount,
				"total_changes", len(pageChanges))
		}

		// Accumulate changes
		allChanges = append(allChanges, pageChanges...)

		return nil
	})

	if err != nil {
		return nil, fmt.Errorf("failed to fetch registry pages: %w", err)
	}

	// Count by change type
	created := 0
	updated := 0
	unchanged := 0
	for _, change := range allChanges {
		switch change.ChangeType {
		case domain.ChangeTypeCreated:
			created++
		case domain.ChangeTypeUpdated:
			updated++
		case domain.ChangeTypeUnchanged:
			unchanged++
		}
	}

	ri.logger.Info("completed registry ingestion",
		"ano_mes", anoMes,
		"total_pages", pageCount,
		"total_institutions", len(allChanges),
		"created", created,
		"updated", updated,
		"unchanged", unchanged)

	return allChanges, nil
}

// buildColumnMap creates a map from column name to index
func (ri *RegistryIngester) buildColumnMap(header []string) map[string]int {
	colMap := make(map[string]int)
	for i, col := range header {
		colMap[col] = i
	}
	return colMap
}

// getColumnValue safely retrieves a column value by name
func (ri *RegistryIngester) getColumnValue(row []string, colMap map[string]int, colName string) string {
	if idx, ok := colMap[colName]; ok && idx < len(row) {
		return row[idx]
	}
	return ""
}

// parseInstitution parses a CSV row into an Institution using column name mapping
func (ri *RegistryIngester) parseInstitution(row []string, colMap map[string]int, anoMes string) (*domain.Institution, error) {
	// CSV columns from BACEN IfDataCadastro (actual column names):
	// CodInst, Data, NomeInstituicao, DataInicioAtividade, Tcb, Td, Tc,
	// SegmentoTb, Atividade, Uf, Municipio, Sr, CodConglomeradoFinanceiro,
	// CodConglomeradoPrudencial, CnpjInstituicaoLider, Situacao

	bacenCodInst := strings.TrimSpace(ri.getColumnValue(row, colMap, "CodInst"))
	if bacenCodInst == "" {
		return nil, fmt.Errorf("empty bacen_cod_inst")
	}

	// Helper function to truncate string to max length
	truncateString := func(s string, maxLen int) string {
		if len(s) > maxLen {
			return s[:maxLen]
		}
		return s
	}

	// Parse and validate fixed-length fields
	situacaoRaw := strings.TrimSpace(ri.getColumnValue(row, colMap, "Situacao"))
	situacao := truncateString(situacaoRaw, 1)
	if len(situacaoRaw) > 1 {
		ri.logger.Warn("situacao value truncated",
			"bacen_cod_inst", bacenCodInst,
			"original", situacaoRaw,
			"truncated", situacao)
	}

	tipoConsolidacaoRaw := strings.TrimSpace(ri.getColumnValue(row, colMap, "Td"))
	tipoConsolidacao := truncateString(tipoConsolidacaoRaw, 1)
	if len(tipoConsolidacaoRaw) > 1 {
		ri.logger.Warn("tipo_consolidacao value truncated",
			"bacen_cod_inst", bacenCodInst,
			"original", tipoConsolidacaoRaw,
			"truncated", tipoConsolidacao)
	}

	ufRaw := strings.TrimSpace(ri.getColumnValue(row, colMap, "Uf"))
	uf := truncateString(ufRaw, 2)
	if len(ufRaw) > 2 {
		ri.logger.Warn("uf value truncated",
			"bacen_cod_inst", bacenCodInst,
			"original", ufRaw,
			"truncated", uf,
			"original_len", len(ufRaw))
	}

	// Helper to convert *string to string (empty if nil)
	stringOrEmpty := func(s *string) string {
		if s == nil {
			return ""
		}
		return *s
	}

	inst := &domain.Institution{
		BacenCodInst:               bacenCodInst,
		AnoMesRef:                  anoMes, // CHAR(6) - validated by caller
		NomeInstituicao:            strings.TrimSpace(ri.getColumnValue(row, colMap, "NomeInstituicao")),
		Situacao:                   stringOrEmpty(shared.NormalizeString(situacao)),
		SegmentoTB:                 shared.NormalizeString(ri.getColumnValue(row, colMap, "SegmentoTb")),
		Atividade:                  shared.NormalizeString(ri.getColumnValue(row, colMap, "Atividade")),
		TipoConsolidadoBancario:    shared.NormalizeString(ri.getColumnValue(row, colMap, "Tcb")),
		TipoConsolidacao:           shared.NormalizeString(tipoConsolidacao),
		TipoControle:               shared.NormalizeString(ri.getColumnValue(row, colMap, "Tc")),
		UF:                         shared.NormalizeString(uf),
		Municipio:                  shared.NormalizeString(ri.getColumnValue(row, colMap, "Municipio")),
		CodConglomeradoFinanceiro:  shared.NormalizeString(ri.getColumnValue(row, colMap, "CodConglomeradoFinanceiro")),
		CodConglomeradoPrudencial:  shared.NormalizeString(ri.getColumnValue(row, colMap, "CodConglomeradoPrudencial")),
		CNPJInstituicaoLider:       shared.NormalizeString(ri.getColumnValue(row, colMap, "CnpjInstituicaoLider")),
	}

	// Compute source hash
	hash, err := ri.computeHash(inst)
	if err != nil {
		return nil, fmt.Errorf("failed to compute hash: %w", err)
	}
	inst.SourceHash = hash

	return inst, nil
}

// detectChange detects the type of change for an institution within a specific quarter
// Checks by composite key (bacen_cod_inst, ano_mes_ref) to determine if this is a new quarter snapshot
// or an update to an existing quarter snapshot
func (ri *RegistryIngester) detectChange(ctx context.Context, inst *domain.Institution) (domain.ChangeType, error) {
	// Try to find existing institution for this specific quarter
	existing, err := ri.institutionRepo.FindByBacenCodInstAndAnoMes(ctx, inst.BacenCodInst, inst.AnoMesRef)
	if err != nil {
		if err == domain.ErrInstitutionNotFound {
			// New institution snapshot for this quarter
			return domain.ChangeTypeCreated, nil
		}
		return "", fmt.Errorf("failed to find institution: %w", err)
	}

	// Compare hashes - if hash differs, data changed within same quarter
	if existing.SourceHash != inst.SourceHash {
		return domain.ChangeTypeUpdated, nil
	}

	// Same (bacen_cod_inst, ano_mes_ref, source_hash) - no change
	return domain.ChangeTypeUnchanged, nil
}

// computeHash computes SHA-256 hash of institution fields
func (ri *RegistryIngester) computeHash(inst *domain.Institution) (string, error) {
	// Create map of fields to hash
	// Include all event-relevant fields for change detection (matching Python implementation)
	fields := map[string]interface{}{
		"bacen_cod_inst":               inst.BacenCodInst,
		"nome_instituicao":             inst.NomeInstituicao,
		"situacao":                     inst.Situacao,
		"segmento_tb":                  ptrToString(inst.SegmentoTB),
		"atividade":                    ptrToString(inst.Atividade),
		"tipo_consolidado_bancario":    ptrToString(inst.TipoConsolidadoBancario),
		"tipo_consolidacao":            ptrToString(inst.TipoConsolidacao),
		"tipo_controle":                ptrToString(inst.TipoControle),
		"uf":                           ptrToString(inst.UF),
		"municipio":                    ptrToString(inst.Municipio),
		"cod_conglomerado_financeiro":  ptrToString(inst.CodConglomeradoFinanceiro),
		"cod_conglomerado_prudencial":  ptrToString(inst.CodConglomeradoPrudencial),
		"cnpj_instituicao_lider":       ptrToString(inst.CNPJInstituicaoLider),
	}

	return shared.ComputeInstitutionHash(fields)
}

// ptrToString converts *string to string (empty if nil)
func ptrToString(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
