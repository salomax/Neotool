package database

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"strings"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// InstitutionRepository handles institution data persistence
type InstitutionRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewInstitutionRepository creates a new institution repository
func NewInstitutionRepository(pool *pgxpool.Pool, logger *slog.Logger) *InstitutionRepository {
	return &InstitutionRepository{
		pool:   pool,
		logger: logger,
	}
}

// Upsert inserts or updates an institution for a specific quarter
// Uses composite key (bacen_cod_inst, ano_mes_ref) for idempotency within same quarter
func (r *InstitutionRepository) Upsert(ctx context.Context, inst *domain.Institution) error {
	query := `
		INSERT INTO financial_data.bacen_institution (
			bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao,
			segmento_tb, atividade, tipo_consolidado_bancario, tipo_consolidacao,
			tipo_controle, uf, municipio, cod_conglomerado_financeiro,
			cod_conglomerado_prudencial, cnpj_instituicao_lider, source_hash
		) VALUES (
			$1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15
		)
		ON CONFLICT (bacen_cod_inst, ano_mes_ref) DO UPDATE SET
			nome_instituicao = EXCLUDED.nome_instituicao,
			situacao = EXCLUDED.situacao,
			segmento_tb = EXCLUDED.segmento_tb,
			atividade = EXCLUDED.atividade,
			tipo_consolidado_bancario = EXCLUDED.tipo_consolidado_bancario,
			tipo_consolidacao = EXCLUDED.tipo_consolidacao,
			tipo_controle = EXCLUDED.tipo_controle,
			uf = EXCLUDED.uf,
			municipio = EXCLUDED.municipio,
			cod_conglomerado_financeiro = EXCLUDED.cod_conglomerado_financeiro,
			cod_conglomerado_prudencial = EXCLUDED.cod_conglomerado_prudencial,
			cnpj_instituicao_lider = EXCLUDED.cnpj_instituicao_lider,
			source_hash = EXCLUDED.source_hash,
			updated_at = NOW()
		RETURNING created_at, updated_at
	`

	err := r.pool.QueryRow(
		ctx, query,
		inst.BacenCodInst, inst.AnoMesRef, inst.NomeInstituicao, inst.Situacao,
		inst.SegmentoTB, inst.Atividade, inst.TipoConsolidadoBancario, inst.TipoConsolidacao,
		inst.TipoControle, inst.UF, inst.Municipio, inst.CodConglomeradoFinanceiro,
		inst.CodConglomeradoPrudencial, inst.CNPJInstituicaoLider, inst.SourceHash,
	).Scan(&inst.CreatedAt, &inst.UpdatedAt)

	if err != nil {
		return fmt.Errorf("failed to upsert institution %s: %w", inst.BacenCodInst, err)
	}

	r.logger.Debug("upserted institution",
		"bacen_cod_inst", inst.BacenCodInst,
		"ano_mes_ref", inst.AnoMesRef)

	return nil
}

// FindByBacenCodInstAndAnoMes retrieves an institution by its code and quarter
func (r *InstitutionRepository) FindByBacenCodInstAndAnoMes(ctx context.Context, bacenCodInst string, anoMes string) (*domain.Institution, error) {
	query := `
		SELECT
			bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao,
			segmento_tb, atividade, tipo_consolidado_bancario, tipo_consolidacao,
			tipo_controle, uf, municipio, cod_conglomerado_financeiro,
			cod_conglomerado_prudencial, cnpj_instituicao_lider, source_hash,
			created_at, updated_at
		FROM financial_data.bacen_institution
		WHERE bacen_cod_inst = $1 AND ano_mes_ref = $2
	`

	inst := &domain.Institution{}
	err := r.pool.QueryRow(ctx, query, bacenCodInst, anoMes).Scan(
		&inst.BacenCodInst, &inst.AnoMesRef, &inst.NomeInstituicao, &inst.Situacao,
		&inst.SegmentoTB, &inst.Atividade, &inst.TipoConsolidadoBancario, &inst.TipoConsolidacao,
		&inst.TipoControle, &inst.UF, &inst.Municipio, &inst.CodConglomeradoFinanceiro,
		&inst.CodConglomeradoPrudencial, &inst.CNPJInstituicaoLider, &inst.SourceHash,
		&inst.CreatedAt, &inst.UpdatedAt,
	)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, domain.ErrInstitutionNotFound
		}
		return nil, fmt.Errorf("failed to find institution %s for quarter %s: %w", bacenCodInst, anoMes, err)
	}

	return inst, nil
}

// FindByBacenCodInst retrieves an institution by its code (for backward compatibility)
// Returns the latest quarter's snapshot for the institution
func (r *InstitutionRepository) FindByBacenCodInst(ctx context.Context, bacenCodInst string) (*domain.Institution, error) {
	query := `
		SELECT
			bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao,
			segmento_tb, atividade, tipo_consolidado_bancario, tipo_consolidacao,
			tipo_controle, uf, municipio, cod_conglomerado_financeiro,
			cod_conglomerado_prudencial, cnpj_instituicao_lider, source_hash,
			created_at, updated_at
		FROM financial_data.bacen_institution
		WHERE bacen_cod_inst = $1
		ORDER BY ano_mes_ref DESC
		LIMIT 1
	`

	inst := &domain.Institution{}
	err := r.pool.QueryRow(ctx, query, bacenCodInst).Scan(
		&inst.BacenCodInst, &inst.AnoMesRef, &inst.NomeInstituicao, &inst.Situacao,
		&inst.SegmentoTB, &inst.Atividade, &inst.TipoConsolidadoBancario, &inst.TipoConsolidacao,
		&inst.TipoControle, &inst.UF, &inst.Municipio, &inst.CodConglomeradoFinanceiro,
		&inst.CodConglomeradoPrudencial, &inst.CNPJInstituicaoLider, &inst.SourceHash,
		&inst.CreatedAt, &inst.UpdatedAt,
	)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, domain.ErrInstitutionNotFound
		}
		return nil, fmt.Errorf("failed to find institution %s: %w", bacenCodInst, err)
	}

	return inst, nil
}

// CountByAnoMes counts institutions for a specific quarter
func (r *InstitutionRepository) CountByAnoMes(ctx context.Context, anoMes string) (int, error) {
	query := `
		SELECT COUNT(*)
		FROM financial_data.bacen_institution
		WHERE ano_mes_ref = $1
	`

	var count int
	err := r.pool.QueryRow(ctx, query, anoMes).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("failed to count institutions for %s: %w", anoMes, err)
	}

	return count, nil
}

// ListByAnoMes retrieves all institutions for a specific quarter
func (r *InstitutionRepository) ListByAnoMes(ctx context.Context, anoMes string) ([]*domain.Institution, error) {
	query := `
		SELECT
			bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao,
			segmento_tb, atividade, tipo_consolidado_bancario, tipo_consolidacao,
			tipo_controle, uf, municipio, cod_conglomerado_financeiro,
			cod_conglomerado_prudencial, cnpj_instituicao_lider, source_hash,
			created_at, updated_at
		FROM financial_data.bacen_institution
		WHERE ano_mes_ref = $1
		ORDER BY bacen_cod_inst
	`

	rows, err := r.pool.Query(ctx, query, anoMes)
	if err != nil {
		return nil, fmt.Errorf("failed to list institutions for %s: %w", anoMes, err)
	}
	defer rows.Close()

	var institutions []*domain.Institution
	for rows.Next() {
		inst := &domain.Institution{}
		if err := rows.Scan(
			&inst.BacenCodInst, &inst.AnoMesRef, &inst.NomeInstituicao, &inst.Situacao,
			&inst.SegmentoTB, &inst.Atividade, &inst.TipoConsolidadoBancario, &inst.TipoConsolidacao,
			&inst.TipoControle, &inst.UF, &inst.Municipio, &inst.CodConglomeradoFinanceiro,
			&inst.CodConglomeradoPrudencial, &inst.CNPJInstituicaoLider, &inst.SourceHash,
			&inst.CreatedAt, &inst.UpdatedAt,
		); err != nil {
			return nil, fmt.Errorf("failed to scan institution row: %w", err)
		}
		institutions = append(institutions, inst)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate institutions for %s: %w", anoMes, err)
	}

	return institutions, nil
}

// ListDistinctAnoMes retrieves all distinct quarters that have institution data
func (r *InstitutionRepository) ListDistinctAnoMes(ctx context.Context) ([]string, error) {
	query := `
		SELECT DISTINCT ano_mes_ref
		FROM financial_data.bacen_institution
		ORDER BY ano_mes_ref
	`

	rows, err := r.pool.Query(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("failed to list distinct ano_mes: %w", err)
	}
	defer rows.Close()

	var quarters []string
	for rows.Next() {
		var anoMes string
		if err := rows.Scan(&anoMes); err != nil {
			return nil, fmt.Errorf("failed to scan ano_mes row: %w", err)
		}
		quarters = append(quarters, anoMes)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate ano_mes rows: %w", err)
	}

	return quarters, nil
}

// GetLastExtractedAnoMes retrieves the last extracted ano_mes from load control
func (r *InstitutionRepository) GetLastExtractedAnoMes(ctx context.Context) (string, error) {
	query := `
		SELECT ano_mes
		FROM financial_data.bacen_ifdata_load_control
		WHERE completed = true
		ORDER BY ano_mes DESC
		LIMIT 1
	`

	var anoMes string
	err := r.pool.QueryRow(ctx, query).Scan(&anoMes)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return "", nil // No data yet, will start from historical start
		}
		return "", fmt.Errorf("failed to get last extracted ano_mes: %w", err)
	}

	return anoMes, nil
}

// ptrToString converts *string to string (empty if nil)
func ptrToString(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

// BatchUpsert inserts or updates multiple institutions in a single transaction
// Uses composite key (bacen_cod_inst, ano_mes_ref) for idempotency within same quarter
// This is more efficient than individual upserts
func (r *InstitutionRepository) BatchUpsert(ctx context.Context, institutions []*domain.Institution) error {
	if len(institutions) == 0 {
		return nil
	}

	query := `
		INSERT INTO financial_data.bacen_institution (
			bacen_cod_inst, ano_mes_ref, nome_instituicao, situacao,
			segmento_tb, atividade, tipo_consolidado_bancario, tipo_consolidacao,
			tipo_controle, uf, municipio, cod_conglomerado_financeiro,
			cod_conglomerado_prudencial, cnpj_instituicao_lider, source_hash
		) VALUES (
			$1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15
		)
		ON CONFLICT (bacen_cod_inst, ano_mes_ref) DO UPDATE SET
			nome_instituicao = EXCLUDED.nome_instituicao,
			situacao = EXCLUDED.situacao,
			segmento_tb = EXCLUDED.segmento_tb,
			atividade = EXCLUDED.atividade,
			tipo_consolidado_bancario = EXCLUDED.tipo_consolidado_bancario,
			tipo_consolidacao = EXCLUDED.tipo_consolidacao,
			tipo_controle = EXCLUDED.tipo_controle,
			uf = EXCLUDED.uf,
			municipio = EXCLUDED.municipio,
			cod_conglomerado_financeiro = EXCLUDED.cod_conglomerado_financeiro,
			cod_conglomerado_prudencial = EXCLUDED.cod_conglomerado_prudencial,
			cnpj_instituicao_lider = EXCLUDED.cnpj_instituicao_lider,
			source_hash = EXCLUDED.source_hash,
			updated_at = NOW()
	`

	// Use batch insert for better performance
	batch := &pgx.Batch{}
	for _, inst := range institutions {
		batch.Queue(query,
			inst.BacenCodInst, inst.AnoMesRef, inst.NomeInstituicao, inst.Situacao,
			inst.SegmentoTB, inst.Atividade, inst.TipoConsolidadoBancario, inst.TipoConsolidacao,
			inst.TipoControle, inst.UF, inst.Municipio, inst.CodConglomeradoFinanceiro,
			inst.CodConglomeradoPrudencial, inst.CNPJInstituicaoLider, inst.SourceHash,
		)
	}

	// Execute batch in a transaction
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback(ctx)

	br := tx.SendBatch(ctx, batch)
	defer br.Close()

	// Execute all queued operations
	for i := 0; i < len(institutions); i++ {
		_, err := br.Exec()
		if err != nil {
			inst := institutions[i]
			// Provide detailed error information for debugging
			// Check all fixed-length fields that could cause "value too long" errors
			anoMesRef := inst.AnoMesRef
			situacao := inst.Situacao // string, not pointer
			tipoConsolidacao := ptrToString(inst.TipoConsolidacao)
			uf := ptrToString(inst.UF)
			
			// Identify which field is likely causing the issue
			var problematicFields []string
			if len(anoMesRef) > 6 {
				problematicFields = append(problematicFields, fmt.Sprintf("ano_mes_ref=%q (len=%d, max=6)", anoMesRef, len(anoMesRef)))
			}
			if len(situacao) > 1 {
				problematicFields = append(problematicFields, fmt.Sprintf("situacao=%q (len=%d, max=1)", situacao, len(situacao)))
			}
			if len(tipoConsolidacao) > 1 {
				problematicFields = append(problematicFields, fmt.Sprintf("tipo_consolidacao=%q (len=%d, max=1)", tipoConsolidacao, len(tipoConsolidacao)))
			}
			if len(uf) > 2 {
				problematicFields = append(problematicFields, fmt.Sprintf("uf=%q (len=%d, max=2)", uf, len(uf)))
			}
			
			errorMsg := fmt.Sprintf("failed to upsert institution in batch at index %d (bacen_cod_inst=%s): %v",
				i, inst.BacenCodInst, err)
			
			if len(problematicFields) > 0 {
				errorMsg += fmt.Sprintf(". Fields exceeding length limits: %s", strings.Join(problematicFields, ", "))
			}
			
			errorMsg += fmt.Sprintf(". All field lengths: ano_mes_ref=%d, situacao=%d, tipo_consolidacao=%d, uf=%d",
				len(anoMesRef), len(situacao), len(tipoConsolidacao), len(uf))
			
			return fmt.Errorf("%s", errorMsg)
		}
	}

	if err := br.Close(); err != nil {
		return fmt.Errorf("failed to close batch: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("failed to commit batch upsert: %w", err)
	}

	r.logger.Debug("batch upserted institutions",
		"count", len(institutions))

	return nil
}
