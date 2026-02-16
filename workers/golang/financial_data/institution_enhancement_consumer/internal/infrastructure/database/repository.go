package database

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/domain"
)

// ParentInstitutionRepository handles database operations for parent institutions
type ParentInstitutionRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewParentInstitutionRepository creates a new repository instance
func NewParentInstitutionRepository(pool *pgxpool.Pool, logger *slog.Logger) *ParentInstitutionRepository {
	return &ParentInstitutionRepository{
		pool:   pool,
		logger: logger,
	}
}

// Upsert inserts or updates a parent institution record
func (r *ParentInstitutionRepository) Upsert(ctx context.Context, institution *domain.ParentInstitution) error {
	query := `
		INSERT INTO financial_data.financial_institution (
			parent_cnpj,
			legal_name,
			trade_name,
			email_domain,
			website,
			asset_id,
			social_network,
			receita_data,
			created_at,
			updated_at
		) VALUES (
			$1, $2, $3, $4, $5, $6, $7, $8, $9, $10
		)
		ON CONFLICT (parent_cnpj) DO UPDATE SET
			legal_name = EXCLUDED.legal_name,
			trade_name = EXCLUDED.trade_name,
			email_domain = EXCLUDED.email_domain,
			website = EXCLUDED.website,
			asset_id = EXCLUDED.asset_id,
			social_network = EXCLUDED.social_network,
			receita_data = EXCLUDED.receita_data,
			updated_at = NOW()
		RETURNING id, created_at, updated_at
	`

	// Convert maps to JSONB
	var socialNetworkJSON []byte
	var receitaDataJSON []byte
	var err error

	if len(institution.SocialNetwork) > 0 {
		socialNetworkJSON, err = json.Marshal(institution.SocialNetwork)
		if err != nil {
			return fmt.Errorf("failed to marshal social_network: %w", err)
		}
	}

	if len(institution.ReceitaData) > 0 {
		receitaDataJSON, err = json.Marshal(institution.ReceitaData)
		if err != nil {
			return fmt.Errorf("failed to marshal receita_data: %w", err)
		}
	}

	var id string
	var createdAt, updatedAt time.Time
	err = r.pool.QueryRow(ctx, query,
		institution.ParentCNPJ,
		institution.LegalName,
		institution.TradeName,
		institution.EmailDomain,
		institution.Website,
		institution.AssetID,
		socialNetworkJSON,
		receitaDataJSON,
		institution.CreatedAt,
		institution.UpdatedAt,
	).Scan(&id, &createdAt, &updatedAt)

	if err != nil {
		return fmt.Errorf("failed to upsert parent institution: %w", err)
	}

	// Update ID and timestamps from database
	institution.ID = &id
	institution.CreatedAt = createdAt
	institution.UpdatedAt = updatedAt

	r.logger.Info("Upserted parent institution",
		"id", id,
		"parent_cnpj", institution.ParentCNPJ,
		"created_at", createdAt,
		"updated_at", updatedAt)

	return nil
}

// FindByParentCNPJ retrieves a parent institution by CNPJ
func (r *ParentInstitutionRepository) FindByParentCNPJ(ctx context.Context, parentCNPJ string) (*domain.ParentInstitution, error) {
	query := `
		SELECT
			id,
			parent_cnpj,
			legal_name,
			trade_name,
			email_domain,
			website,
			asset_id,
			social_network,
			receita_data,
			created_at,
			updated_at
		FROM financial_data.financial_institution
		WHERE parent_cnpj = $1
	`

	var institution domain.ParentInstitution
	var id string
	var socialNetworkJSON, receitaDataJSON []byte

	err := r.pool.QueryRow(ctx, query, parentCNPJ).Scan(
		&id,
		&institution.ParentCNPJ,
		&institution.LegalName,
		&institution.TradeName,
		&institution.EmailDomain,
		&institution.Website,
		&institution.AssetID,
		&socialNetworkJSON,
		&receitaDataJSON,
		&institution.CreatedAt,
		&institution.UpdatedAt,
	)

	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, fmt.Errorf("parent institution not found: %s", parentCNPJ)
		}
		return nil, fmt.Errorf("failed to query parent institution: %w", err)
	}

	// Only set ID if scan succeeded
	institution.ID = &id

	// Unmarshal JSONB fields
	if len(socialNetworkJSON) > 0 {
		if err := json.Unmarshal(socialNetworkJSON, &institution.SocialNetwork); err != nil {
			return nil, fmt.Errorf("failed to unmarshal social_network: %w", err)
		}
	}

	if len(receitaDataJSON) > 0 {
		if err := json.Unmarshal(receitaDataJSON, &institution.ReceitaData); err != nil {
			return nil, fmt.Errorf("failed to unmarshal receita_data: %w", err)
		}
	}

	return &institution, nil
}

// FindByParentCNPJUpdatedSince retrieves a parent institution by CNPJ only if updated_at >= since.
// Returns (nil, nil) when no row exists or updated_at < since (caller treats as "no recent record").
func (r *ParentInstitutionRepository) FindByParentCNPJUpdatedSince(ctx context.Context, parentCNPJ string, since time.Time) (*domain.ParentInstitution, error) {
	query := `
		SELECT
			id,
			parent_cnpj,
			legal_name,
			trade_name,
			email_domain,
			website,
			asset_id,
			social_network,
			receita_data,
			created_at,
			updated_at
		FROM financial_data.financial_institution
		WHERE parent_cnpj = $1 AND updated_at >= $2
	`

	var institution domain.ParentInstitution
	var id string
	var socialNetworkJSON, receitaDataJSON []byte

	err := r.pool.QueryRow(ctx, query, parentCNPJ, since).Scan(
		&id,
		&institution.ParentCNPJ,
		&institution.LegalName,
		&institution.TradeName,
		&institution.EmailDomain,
		&institution.Website,
		&institution.AssetID,
		&socialNetworkJSON,
		&receitaDataJSON,
		&institution.CreatedAt,
		&institution.UpdatedAt,
	)

	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("failed to query parent institution: %w", err)
	}

	institution.ID = &id

	if len(socialNetworkJSON) > 0 {
		if err := json.Unmarshal(socialNetworkJSON, &institution.SocialNetwork); err != nil {
			return nil, fmt.Errorf("failed to unmarshal social_network: %w", err)
		}
	}

	if len(receitaDataJSON) > 0 {
		if err := json.Unmarshal(receitaDataJSON, &institution.ReceitaData); err != nil {
			return nil, fmt.Errorf("failed to unmarshal receita_data: %w", err)
		}
	}

	return &institution, nil
}

// Exists checks if a parent institution exists
func (r *ParentInstitutionRepository) Exists(ctx context.Context, parentCNPJ string) (bool, error) {
	query := `SELECT EXISTS(SELECT 1 FROM financial_data.financial_institution WHERE parent_cnpj = $1)`

	var exists bool
	err := r.pool.QueryRow(ctx, query, parentCNPJ).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("failed to check existence: %w", err)
	}

	return exists, nil
}

// LinkBacenInstitution links a bacen_institution to a financial_institution
// Inserts or updates the relation table entry
func (r *ParentInstitutionRepository) LinkBacenInstitution(ctx context.Context, financialInstitutionID string, bacenCodInst string, anoMesRef string) error {
	// Insert or update relation table entry
	relationQuery := `
		INSERT INTO financial_data.financial_institution_bacen_institution (
			financial_institution_id,
			bacen_cod_inst,
			ano_mes_ref
		) VALUES (
			$1, $2, $3
		)
		ON CONFLICT (financial_institution_id, bacen_cod_inst, ano_mes_ref) DO NOTHING
	`
	_, err := r.pool.Exec(ctx, relationQuery, financialInstitutionID, bacenCodInst, anoMesRef)
	if err != nil {
		return fmt.Errorf("failed to insert relation table entry: %w", err)
	}

	r.logger.Info("Linked bacen_institution to financial_institution",
		"financial_institution_id", financialInstitutionID,
		"bacen_cod_inst", bacenCodInst,
		"ano_mes_ref", anoMesRef)

	return nil
}

// LinkAllBacenInstitutionsByParentCNPJ links all bacen_institution records
// that would construct the same parent_cnpj to the given financial_institution.
// This ensures that all institutions with the same cnpj_instituicao_lider
// (or that would construct the same parent_cnpj) are linked to the same financial_institution.
//
// The matching logic:
// 1. Extract base 8 digits from parent_cnpj (first 8 digits)
// 2. Find all bacen_institution records where:
//    - If bacen_cod_inst does NOT start with 'C': use bacen_cod_inst (institution's own CNPJ root)
//    - If bacen_cod_inst starts with 'C' (conglomerate): use cnpj_instituicao_lider
// messageBacenCodInst and messageAnoMesRef are the values from the Kafka message for the specific record being processed.
func (r *ParentInstitutionRepository) LinkAllBacenInstitutionsByParentCNPJ(ctx context.Context, financialInstitutionID string, parentCNPJ string, messageBacenCodInst string, messageAnoMesRef string) error {
	// Extract base 8 digits from parent_cnpj (first 8 digits before validator digits)
	// parent_cnpj format: 8 base digits + "0001" + 2 validator digits = 14 digits total
	if len(parentCNPJ) < 8 {
		return fmt.Errorf("invalid parent_cnpj format: %s (must be at least 8 digits)", parentCNPJ)
	}
	base8Digits := parentCNPJ[:8]
	
	// Find all bacen_institution records that would construct the same parent_cnpj
	// Get ALL (bacen_cod_inst, ano_mes_ref) combinations, not just one per bacen_cod_inst
	// Logic (matching ConstructParentCNPJ priority):
	// 1. If bacen_cod_inst does NOT start with 'C', use bacen_cod_inst (it's the institution's own CNPJ root)
	// 2. If bacen_cod_inst starts with 'C' (conglomerate), use cnpj_instituicao_lider
	// We match on the base 8 digits since CNPJ construction is deterministic
	findQuery := `
		SELECT DISTINCT
			bacen_cod_inst,
			ano_mes_ref
		FROM financial_data.bacen_institution
		WHERE (
			-- Case 1: bacen_cod_inst does NOT start with 'C' - use bacen_cod_inst as the institution's CNPJ root
			bacen_cod_inst IS NOT NULL
			AND UPPER(SUBSTRING(TRIM(bacen_cod_inst), 1, 1)) != 'C'
			AND LPAD(REGEXP_REPLACE(bacen_cod_inst, '[^0-9]', '', 'g'), 8, '0') = $1
		) OR (
			-- Case 2: bacen_cod_inst starts with 'C' (conglomerate) - use cnpj_instituicao_lider
			bacen_cod_inst IS NOT NULL
			AND UPPER(SUBSTRING(TRIM(bacen_cod_inst), 1, 1)) = 'C'
			AND cnpj_instituicao_lider IS NOT NULL AND TRIM(cnpj_instituicao_lider) != ''
			AND LPAD(REGEXP_REPLACE(cnpj_instituicao_lider, '[^0-9]', '', 'g'), 8, '0') = $1
		)
		ORDER BY bacen_cod_inst, ano_mes_ref DESC
	`
	
	rows, err := r.pool.Query(ctx, findQuery, base8Digits)
	if err != nil {
		return fmt.Errorf("failed to find bacen_institution records: %w", err)
	}
	defer rows.Close()
	
	type bacenRecord struct {
		codInst  string
		anoMesRef string
	}
	bacenRecordsMap := make(map[string]map[string]bool) // bacen_cod_inst -> ano_mes_ref -> exists
	
	// Collect all (bacen_cod_inst, ano_mes_ref) pairs from database
	for rows.Next() {
		var record bacenRecord
		if err := rows.Scan(&record.codInst, &record.anoMesRef); err != nil {
			return fmt.Errorf("failed to scan bacen_cod_inst and ano_mes_ref: %w", err)
		}
		if bacenRecordsMap[record.codInst] == nil {
			bacenRecordsMap[record.codInst] = make(map[string]bool)
		}
		bacenRecordsMap[record.codInst][record.anoMesRef] = true
	}
	
	if err := rows.Err(); err != nil {
		return fmt.Errorf("error iterating rows: %w", err)
	}
	
	// If message provides ano_mes_ref, ensure that specific combination is included
	if messageAnoMesRef != "" && messageBacenCodInst != "" {
		if bacenRecordsMap[messageBacenCodInst] == nil {
			bacenRecordsMap[messageBacenCodInst] = make(map[string]bool)
		}
		bacenRecordsMap[messageBacenCodInst][messageAnoMesRef] = true
	}
	
	// Convert map to slice for linking
	var bacenRecords []bacenRecord
	for codInst, anoMesRefs := range bacenRecordsMap {
		for anoMesRef := range anoMesRefs {
			bacenRecords = append(bacenRecords, bacenRecord{
				codInst:  codInst,
				anoMesRef: anoMesRef,
			})
		}
	}
	
	if len(bacenRecords) == 0 {
		r.logger.Warn("No bacen_institution records found for parent_cnpj",
			"financial_institution_id", financialInstitutionID,
			"parent_cnpj", parentCNPJ,
			"base8_digits", base8Digits)
		return nil
	}
	
	// Link all found bacen_institution records
	linkQuery := `
		INSERT INTO financial_data.financial_institution_bacen_institution (
			financial_institution_id,
			bacen_cod_inst,
			ano_mes_ref
		) VALUES (
			$1, $2, $3
		)
		ON CONFLICT (financial_institution_id, bacen_cod_inst, ano_mes_ref) DO NOTHING
	`
	
	linkedCount := 0
	uniqueBacenCodInsts := make(map[string]bool)
	for _, record := range bacenRecords {
		_, err := r.pool.Exec(ctx, linkQuery, financialInstitutionID, record.codInst, record.anoMesRef)
		if err != nil {
			return fmt.Errorf("failed to link bacen_institution %s (ano_mes_ref: %s): %w", record.codInst, record.anoMesRef, err)
		}
		uniqueBacenCodInsts[record.codInst] = true
		linkedCount++
	}
	
	var bacenCodInsts []string
	for codInst := range uniqueBacenCodInsts {
		bacenCodInsts = append(bacenCodInsts, codInst)
	}
	
	r.logger.Info("Linked all bacen_institution records to financial_institution",
		"financial_institution_id", financialInstitutionID,
		"parent_cnpj", parentCNPJ,
		"base8_digits", base8Digits,
		"linked_count", linkedCount,
		"unique_bacen_cod_insts", len(bacenCodInsts),
		"bacen_cod_insts", bacenCodInsts)

	return nil
}

// InstitutionWithoutLogo represents a minimal institution record for logo processing
type InstitutionWithoutLogo struct {
	ID         string
	ParentCNPJ string
	Website    string
}

// FindInstitutionsWithoutLogo finds all institutions that have a website but no asset_id
func (r *ParentInstitutionRepository) FindInstitutionsWithoutLogo(ctx context.Context) ([]InstitutionWithoutLogo, error) {
	query := `
		SELECT id, parent_cnpj, website
		FROM financial_data.financial_institution
		WHERE website IS NOT NULL
		  AND website != ''
		  AND asset_id IS NULL
		ORDER BY parent_cnpj
	`

	rows, err := r.pool.Query(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("failed to query institutions without logo: %w", err)
	}
	defer rows.Close()

	var institutions []InstitutionWithoutLogo
	for rows.Next() {
		var inst InstitutionWithoutLogo
		if err := rows.Scan(&inst.ID, &inst.ParentCNPJ, &inst.Website); err != nil {
			return nil, fmt.Errorf("failed to scan institution: %w", err)
		}
		institutions = append(institutions, inst)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating rows: %w", err)
	}

	r.logger.Info("Found institutions without logo", "count", len(institutions))
	return institutions, nil
}

// UpdateAssetID updates only the asset_id field for a given institution
func (r *ParentInstitutionRepository) UpdateAssetID(ctx context.Context, id string, assetID string) error {
	query := `
		UPDATE financial_data.financial_institution
		SET asset_id = $1, updated_at = NOW()
		WHERE id = $2
	`

	result, err := r.pool.Exec(ctx, query, assetID, id)
	if err != nil {
		return fmt.Errorf("failed to update asset_id: %w", err)
	}

	if result.RowsAffected() == 0 {
		return fmt.Errorf("no institution found with id: %s", id)
	}

	r.logger.Info("Updated asset_id for institution",
		"id", id,
		"asset_id", assetID)

	return nil
}
