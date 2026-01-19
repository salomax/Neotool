package database

import (
	"context"
	"errors"
	"fmt"
	"log/slog"

	"github.com/google/uuid"
	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// BankReportAccountRepository handles bank report account metadata persistence
type BankReportAccountRepository struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewBankReportAccountRepository creates a new bank report account repository
func NewBankReportAccountRepository(pool *pgxpool.Pool, logger *slog.Logger) *BankReportAccountRepository {
	return &BankReportAccountRepository{
		pool:   pool,
		logger: logger,
	}
}

// UpsertAccount inserts or updates a bank report account (idempotent)
// Uses ON CONFLICT to handle unique constraint on (grupo, conta, nome_coluna, descricao_coluna)
func (r *BankReportAccountRepository) UpsertAccount(ctx context.Context, account *domain.InstitutionReportAccount) error {
	query := `
		INSERT INTO financial_data.bacen_inst_report_account (
			id, canonical_account_id, grupo, conta, nome_coluna, descricao_coluna
		) VALUES ($1, $2, $3, $4, $5, $6)
		ON CONFLICT (grupo, conta, nome_coluna, descricao_coluna) 
		DO UPDATE SET
			canonical_account_id = COALESCE(
				financial_data.bacen_inst_report_account.canonical_account_id,
				EXCLUDED.canonical_account_id
			),
			updated_at = NOW()
		RETURNING id, canonical_account_id, created_at, updated_at
	`

	// Generate UUID v7 if not set
	if account.ID == "" {
		account.ID = uuid.New().String()
	}

	err := r.pool.QueryRow(
		ctx, query,
		account.ID, account.CanonicalAccountID, account.Grupo, account.Conta, account.NomeColuna, account.DescricaoColuna,
	).Scan(&account.ID, &account.CanonicalAccountID, &account.CreatedAt, &account.UpdatedAt)

	if err != nil {
		return fmt.Errorf("failed to upsert bank report account: %w", err)
	}

	r.logger.Debug("upserted bank report account",
		"id", account.ID,
		"canonical_account_id", account.CanonicalAccountID,
		"grupo", account.Grupo,
		"conta", account.Conta,
		"nome_coluna", account.NomeColuna)

	return nil
}

// FindByKey finds an account by its unique key (grupo, conta, nome_coluna, descricao_coluna)
// Uses COALESCE to handle NULL comparisons correctly
func (r *BankReportAccountRepository) FindByKey(
	ctx context.Context,
	grupo *string,
	conta *string,
	nomeColuna *string,
	descricaoColuna *string,
) (*domain.InstitutionReportAccount, error) {
	query := `
		SELECT id, canonical_account_id, grupo, conta, nome_coluna, descricao_coluna, created_at, updated_at
		FROM financial_data.bacen_inst_report_account
		WHERE COALESCE(grupo, '') = COALESCE($1, '')
		  AND COALESCE(conta, '') = COALESCE($2, '')
		  AND COALESCE(nome_coluna, '') = COALESCE($3, '')
		  AND COALESCE(descricao_coluna, '') = COALESCE($4, '')
		LIMIT 1
	`

	var account domain.InstitutionReportAccount
	err := r.pool.QueryRow(ctx, query, grupo, conta, nomeColuna, descricaoColuna).Scan(
		&account.ID,
		&account.CanonicalAccountID,
		&account.Grupo,
		&account.Conta,
		&account.NomeColuna,
		&account.DescricaoColuna,
		&account.CreatedAt,
		&account.UpdatedAt,
	)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, nil
		}
		return nil, fmt.Errorf("failed to find bank report account: %w", err)
	}

	return &account, nil
}

// GetOrCreateAccount gets an existing account or creates a new one
// This is a convenience method that combines FindByKey and UpsertAccount
func (r *BankReportAccountRepository) GetOrCreateAccount(
	ctx context.Context,
	grupo *string,
	conta *string,
	nomeColuna *string,
	descricaoColuna *string,
	canonicalAccountID *string,
) (*domain.InstitutionReportAccount, error) {
	// Try to find existing account
	account, err := r.FindByKey(ctx, grupo, conta, nomeColuna, descricaoColuna)
	if err != nil {
		return nil, err
	}

	// If found, return it
	if account != nil {
		if account.CanonicalAccountID == nil && canonicalAccountID != nil {
			account.CanonicalAccountID = canonicalAccountID
			if err := r.UpsertAccount(ctx, account); err != nil {
				return nil, err
			}
		}
		return account, nil
	}

	// Otherwise, create a new one
	newAccount := &domain.InstitutionReportAccount{
		CanonicalAccountID: canonicalAccountID,
		Grupo:              grupo,
		Conta:              conta,
		NomeColuna:         nomeColuna,
		DescricaoColuna:    descricaoColuna,
	}

	if err := r.UpsertAccount(ctx, newAccount); err != nil {
		return nil, err
	}

	return newAccount, nil
}
