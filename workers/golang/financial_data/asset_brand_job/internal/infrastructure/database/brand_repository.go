package database

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

const (
	findBrandSQL = `
		SELECT id, status
		FROM financial_data.asset_brand
		WHERE normalized_name = $1
	`

	insertBrandSQL = `
		INSERT INTO financial_data.asset_brand
			(normalized_name, display_name, domain, logo_url, source, confidence, status, raw_search_payload, created_at, updated_at)
		VALUES
			($1, $2, $3, $4, $5, $6, $7, $8, NOW(), NOW())
		RETURNING id
	`

	updateBrandSQL = `
		UPDATE financial_data.asset_brand
		SET display_name = $2,
		    domain = $3,
		    logo_url = $4,
		    source = $5,
		    confidence = $6,
		    status = $7,
		    raw_search_payload = $8,
		    updated_at = NOW()
		WHERE id = $1
	`

	findMatchStatusSQL = `
		SELECT status
		FROM financial_data.investment_asset_brand_match
		WHERE investment_asset_id = $1
		  AND asset_brand_id = $2
	`

	insertMatchSQL = `
		INSERT INTO financial_data.investment_asset_brand_match
			(investment_asset_id, asset_brand_id, query_used, candidate_rank, match_source, match_confidence, status, raw_match_payload, created_at, updated_at)
		VALUES
			($1, $2, $3, $4, $5, $6, $7, $8, NOW(), NOW())
	`

	updateMatchSQL = `
		UPDATE financial_data.investment_asset_brand_match
		SET query_used = $3,
		    candidate_rank = $4,
		    match_source = $5,
		    match_confidence = $6,
		    status = $7,
		    raw_match_payload = $8,
		    updated_at = NOW()
		WHERE investment_asset_id = $1
		  AND asset_brand_id = $2
	`
)

type BrandRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewBrandRepository(pool *pgxpool.Pool, logger *slog.Logger) *BrandRepository {
	return &BrandRepository{pool: pool, logger: logger}
}

func (r *BrandRepository) UpsertBrandCandidate(ctx context.Context, input domain.BrandUpsertInput) error {
	tx, err := r.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return fmt.Errorf("begin transaction: %w", err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	brandID, err := r.upsertBrand(ctx, tx, input)
	if err != nil {
		return err
	}
	if err := r.upsertMatch(ctx, tx, brandID, input); err != nil {
		return err
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit transaction: %w", err)
	}
	return nil
}

func (r *BrandRepository) upsertBrand(ctx context.Context, tx pgx.Tx, input domain.BrandUpsertInput) (uuid.UUID, error) {
	var brandID uuid.UUID
	var currentStatus string

	err := tx.QueryRow(ctx, findBrandSQL, input.NormalizedName).Scan(&brandID, &currentStatus)
	if err != nil && err != pgx.ErrNoRows {
		return uuid.Nil, fmt.Errorf("find brand: %w", err)
	}

	mergedStatus := mergeStatus(currentStatus, input.Status)

	if err == pgx.ErrNoRows {
		if err := tx.QueryRow(
			ctx,
			insertBrandSQL,
			input.NormalizedName,
			input.DisplayName,
			input.Domain,
			input.LogoURL,
			input.Source,
			input.Confidence,
			mergedStatus,
			input.RawSearchPayload,
		).Scan(&brandID); err != nil {
			return uuid.Nil, fmt.Errorf("insert brand: %w", err)
		}
		return brandID, nil
	}

	if _, err := tx.Exec(
		ctx,
		updateBrandSQL,
		brandID,
		input.DisplayName,
		input.Domain,
		input.LogoURL,
		input.Source,
		input.Confidence,
		mergedStatus,
		input.RawSearchPayload,
	); err != nil {
		return uuid.Nil, fmt.Errorf("update brand: %w", err)
	}

	return brandID, nil
}

func (r *BrandRepository) upsertMatch(ctx context.Context, tx pgx.Tx, brandID uuid.UUID, input domain.BrandUpsertInput) error {
	var currentStatus string
	err := tx.QueryRow(ctx, findMatchStatusSQL, input.AssetID, brandID).Scan(&currentStatus)
	if err != nil && err != pgx.ErrNoRows {
		return fmt.Errorf("find match: %w", err)
	}

	mergedStatus := mergeStatus(currentStatus, input.Status)
	if err == pgx.ErrNoRows {
		if _, err := tx.Exec(
			ctx,
			insertMatchSQL,
			input.AssetID,
			brandID,
			input.QueryUsed,
			input.CandidateRank,
			input.Source,
			input.Confidence,
			mergedStatus,
			input.RawSearchPayload,
		); err != nil {
			return fmt.Errorf("insert match: %w", err)
		}
		return nil
	}

	if _, err := tx.Exec(
		ctx,
		updateMatchSQL,
		input.AssetID,
		brandID,
		input.QueryUsed,
		input.CandidateRank,
		input.Source,
		input.Confidence,
		mergedStatus,
		input.RawSearchPayload,
	); err != nil {
		return fmt.Errorf("update match: %w", err)
	}

	return nil
}

func mergeStatus(current string, incoming string) string {
	switch {
	case current == domain.StatusVerified || incoming == domain.StatusVerified:
		return domain.StatusVerified
	case current == domain.StatusAutoApproved || incoming == domain.StatusAutoApproved:
		return domain.StatusAutoApproved
	case current == domain.StatusRejected && incoming == domain.StatusPending:
		return current
	case incoming != "":
		return incoming
	default:
		return current
	}
}
