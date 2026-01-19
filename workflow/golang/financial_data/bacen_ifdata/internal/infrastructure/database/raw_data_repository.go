package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/jackc/pgx/v5/pgxpool"
)

// RawDataRepository handles raw CSV data persistence
type RawDataRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewRawDataRepository creates a new raw data repository
func NewRawDataRepository(pool *pgxpool.Pool, logger *slog.Logger) *RawDataRepository {
	return &RawDataRepository{
		pool:   pool,
		logger: logger,
	}
}

// PurgeByScope deletes existing raw data for a specific extraction scope
// This is called only on the first page (page_skip=0) to ensure idempotency
func (r *RawDataRepository) PurgeByScope(
	ctx context.Context,
	anoMes string,
	tipoInstituicao int,
	nomeRelatorio string,
	numeroRelatorio string,
) error {
	query := `
		DELETE FROM financial_data.bacen_ifdata_raw
		WHERE ano_mes = $1
		  AND tipo_instituicao = $2
		  AND nome_relatorio = $3
		  AND numero_relatorio = $4
	`

	result, err := r.pool.Exec(ctx, query, anoMes, tipoInstituicao, nomeRelatorio, numeroRelatorio)
	if err != nil {
		return fmt.Errorf("failed to purge raw data: %w", err)
	}

	rowsDeleted := result.RowsAffected()
	r.logger.Info("purged raw data for scope",
		"ano_mes", anoMes,
		"tipo_instituicao", tipoInstituicao,
		"nome_relatorio", nomeRelatorio,
		"numero_relatorio", numeroRelatorio,
		"rows_deleted", rowsDeleted)

	return nil
}

// InsertBatch inserts a batch of raw CSV rows
func (r *RawDataRepository) InsertBatch(ctx context.Context, rows []domain.IFDataRaw) (int, error) {
	if len(rows) == 0 {
		return 0, nil
	}

	// Use batch insert for better performance
	query := `
		INSERT INTO financial_data.bacen_ifdata_raw (
			ano_mes, tipo_instituicao, nome_relatorio, numero_relatorio,
			cod_inst, raw_data
		) VALUES ($1, $2, $3, $4, $5, $6)
	`

	// Begin transaction
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return 0, fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback(ctx)

	// Insert rows
	for _, row := range rows {
		_, err := tx.Exec(ctx, query,
			row.AnoMes, row.TipoInstituicao, row.NomeRelatorio, row.NumeroRelatorio,
			row.CodInst, row.RawData)
		if err != nil {
			return 0, fmt.Errorf("failed to insert raw data row: %w", err)
		}
	}

	// Commit transaction
	if err := tx.Commit(ctx); err != nil {
		return 0, fmt.Errorf("failed to commit transaction: %w", err)
	}

	r.logger.Debug("inserted raw data batch",
		"rows_inserted", len(rows),
		"ano_mes", rows[0].AnoMes,
		"tipo_instituicao", rows[0].TipoInstituicao,
		"nome_relatorio", rows[0].NomeRelatorio)

	return len(rows), nil
}

// CountByReport counts raw data rows for a specific report
func (r *RawDataRepository) CountByReport(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
	numeroRelatorio string,
) (int, error) {
	query := `
		SELECT COUNT(*)
		FROM financial_data.bacen_ifdata_raw
		WHERE ano_mes = $1
		  AND nome_relatorio = $2
		  AND numero_relatorio = $3
	`

	var count int
	err := r.pool.QueryRow(ctx, query, anoMes, nomeRelatorio, numeroRelatorio).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("failed to count raw data: %w", err)
	}

	return count, nil
}

// CountByScope counts raw data rows for a specific extraction scope
func (r *RawDataRepository) CountByScope(
	ctx context.Context,
	anoMes string,
	tipoInstituicao int,
	nomeRelatorio string,
	numeroRelatorio string,
) (int, error) {
	query := `
		SELECT COUNT(*)
		FROM financial_data.bacen_ifdata_raw
		WHERE ano_mes = $1
		  AND tipo_instituicao = $2
		  AND nome_relatorio = $3
		  AND numero_relatorio = $4
	`

	var count int
	err := r.pool.QueryRow(ctx, query, anoMes, tipoInstituicao, nomeRelatorio, numeroRelatorio).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("failed to count raw data by scope: %w", err)
	}

	return count, nil
}

// GetRawDataForPromotion retrieves raw data for promotion to hot tables
// Only includes data from active institutions
func (r *RawDataRepository) GetRawDataForPromotion(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
	numeroRelatorio string,
) ([]domain.IFDataRaw, error) {
	query := `
		SELECT
			r.ano_mes, r.tipo_instituicao, r.nome_relatorio, r.numero_relatorio,
			r.cod_inst, r.raw_data, r.created_at, r.updated_at
		FROM financial_data.bacen_ifdata_raw r
		INNER JOIN financial_data.bacen_institution i
			ON r.cod_inst = i.bacen_cod_inst
			AND r.ano_mes = i.ano_mes_ref
		WHERE r.ano_mes = $1
		  AND r.nome_relatorio = $2
		  AND r.numero_relatorio = $3
		  AND i.situacao = 'A'
		ORDER BY r.cod_inst, r.tipo_instituicao
	`

	rows, err := r.pool.Query(ctx, query, anoMes, nomeRelatorio, numeroRelatorio)
	if err != nil {
		return nil, fmt.Errorf("failed to get raw data for promotion: %w", err)
	}
	defer rows.Close()

	var results []domain.IFDataRaw
	for rows.Next() {
		var row domain.IFDataRaw
		if err := rows.Scan(
			&row.AnoMes, &row.TipoInstituicao, &row.NomeRelatorio, &row.NumeroRelatorio,
			&row.CodInst, &row.RawData, &row.CreatedAt, &row.UpdatedAt,
		); err != nil {
			return nil, fmt.Errorf("failed to scan raw data row: %w", err)
		}
		results = append(results, row)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating raw data rows: %w", err)
	}

	r.logger.Debug("retrieved raw data for promotion",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio,
		"numero_relatorio", numeroRelatorio,
		"rows_count", len(results))

	return results, nil
}
