package database

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// BankReportRepository handles bank report data persistence
type BankReportRepository struct {
	pool           *pgxpool.Pool
	accountRepo    *BankReportAccountRepository
	canonicalRepo  *BankReportAccountCanonicalRepository
	logger         *slog.Logger
}

// NewBankReportRepository creates a new bank report repository
func NewBankReportRepository(pool *pgxpool.Pool, logger *slog.Logger) *BankReportRepository {
	return &BankReportRepository{
		pool:          pool,
		accountRepo:   NewBankReportAccountRepository(pool, logger),
		canonicalRepo: NewBankReportAccountCanonicalRepository(pool, logger),
		logger:        logger,
	}
}

// flowAccountMonths maps canonical account names to the months where they report cumulative balances
// and need quarter adjustment (saldo_ajustado = saldo(current) - saldo(previous_quarter)).
var flowAccountMonths = map[string][]string{
	"LUCRO_LIQUIDO": {"06", "12"},
}

// UpsertBankReport inserts or updates a bank report (parent)
func (r *BankReportRepository) UpsertBankReport(ctx context.Context, report *domain.InstitutionReport) error {
	query := `
		INSERT INTO financial_data.bacen_inst_report (
			id, cod_inst, ano_mes, numero_relatorio, tipo_instituicao
		) VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (cod_inst, ano_mes, numero_relatorio, tipo_instituicao) DO UPDATE SET
			updated_at = NOW()
		RETURNING id, created_at, updated_at
	`

	// Generate UUID v7 if not set
	if report.ID == "" {
		report.ID = uuid.New().String()
	}

	err := r.pool.QueryRow(
		ctx, query,
		report.ID, report.CodInst, report.AnoMes,
		report.NumeroRelatorio, report.TipoInstituicao,
	).Scan(&report.ID, &report.CreatedAt, &report.UpdatedAt)

	if err != nil {
		return fmt.Errorf("failed to upsert bank report: %w", err)
	}

	r.logger.Debug("upserted bank report",
		"id", report.ID,
		"cod_inst", report.CodInst,
		"ano_mes", report.AnoMes,
		"numero_relatorio", report.NumeroRelatorio)

	return nil
}

// UpsertBankReportData inserts or updates bank report data (child)
// Uses a check-then-insert/update pattern since there's no unique constraint on (bank_report_id, account_id)
func (r *BankReportRepository) UpsertBankReportData(ctx context.Context, data *domain.InstitutionReportData) error {
	// First, check if a record exists with the same inst_report_id and account_id
	checkQuery := `
		SELECT id, created_at, updated_at
		FROM financial_data.bacen_inst_report_data
		WHERE inst_report_id = $1 AND account_id = $2
		LIMIT 1
	`

	var existingID string
	var existingCreatedAt, existingUpdatedAt time.Time
	err := r.pool.QueryRow(ctx, checkQuery, data.InstReportID, data.AccountID).Scan(
		&existingID, &existingCreatedAt, &existingUpdatedAt)

	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return fmt.Errorf("failed to check existing bank report data: %w", err)
	}

	if err == nil {
		// Record exists, update it
		updateQuery := `
			UPDATE financial_data.bacen_inst_report_data
			SET
				saldo = $1,
				saldo_ajustado = $2,
				updated_at = NOW()
			WHERE id = $3
			RETURNING id, created_at, updated_at
		`

		err = r.pool.QueryRow(ctx, updateQuery,
			data.Saldo, data.SaldoAjustado, existingID,
		).Scan(&data.ID, &data.CreatedAt, &data.UpdatedAt)

		if err != nil {
			return fmt.Errorf("failed to update bank report data: %w", err)
		}

		return nil
	}

	// Record doesn't exist, insert it
	// Generate UUID v7 if not set
	if data.ID == "" {
		data.ID = uuid.New().String()
	}

	insertQuery := `
		INSERT INTO financial_data.bacen_inst_report_data (
			id, inst_report_id, account_id, saldo, saldo_ajustado
		) VALUES ($1, $2, $3, $4, $5)
		RETURNING id, created_at, updated_at
	`

	err = r.pool.QueryRow(
		ctx, insertQuery,
		data.ID, data.InstReportID, data.AccountID, data.Saldo, data.SaldoAjustado,
	).Scan(&data.ID, &data.CreatedAt, &data.UpdatedAt)

	if err != nil {
		return fmt.Errorf("failed to insert bank report data: %w", err)
	}

	return nil
}

// getPriorQuarterBalance fetches the saldo for the previous quarter for a given account
// Returns nil if the prior quarter data is not found
func (r *BankReportRepository) getPriorQuarterBalance(
	ctx context.Context,
	codInst string,
	anoMes string,
	numeroRelatorio string,
	tipoInstituicao int,
	accountID string,
) (*float64, error) {
	// Calculate previous quarter ano_mes
	// Month 06 (Q2 end) -> 03 (Q1 end), Month 12 (Q4 end) -> 09 (Q3 end)
	if len(anoMes) != 6 {
		return nil, fmt.Errorf("invalid ano_mes format: %s", anoMes)
	}

	yearStr := anoMes[:4]
	monthStr := anoMes[4:6]

	var priorMonth string
	if monthStr == "06" {
		priorMonth = "03"
	} else if monthStr == "12" {
		priorMonth = "09"
	} else {
		// Not a flow month, no prior quarter needed
		return nil, nil
	}

	priorAnoMes := yearStr + priorMonth

	// Query for prior quarter balance
	query := `
		SELECT brd.saldo
		FROM financial_data.bacen_inst_report br
		INNER JOIN financial_data.bacen_inst_report_data brd ON brd.inst_report_id = br.id
		WHERE br.cod_inst = $1
		  AND br.ano_mes = $2
		  AND br.numero_relatorio = $3
		  AND br.tipo_instituicao = $4
		  AND brd.account_id = $5
		LIMIT 1
	`

	var priorSaldo *float64
	err := r.pool.QueryRow(ctx, query, codInst, priorAnoMes, numeroRelatorio, tipoInstituicao, accountID).Scan(&priorSaldo)

	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			// Prior quarter data not found, return nil (saldo_ajustado will be NULL)
			return nil, nil
		}
		return nil, fmt.Errorf("failed to get prior quarter balance: %w", err)
	}

	return priorSaldo, nil
}

// isFlowAccountMonth checks if an account needs quarter adjustment for the given month
// Returns true if the account is configured for flow adjustment and the month matches
func isFlowAccountMonth(canonicalName *string, anoMes string) bool {
	if canonicalName == nil {
		return false
	}
	if len(anoMes) != 6 {
		return false
	}

	// Get the months that require adjustment for this account
	flowMonths, exists := flowAccountMonths[*canonicalName]
	if !exists {
		return false
	}

	// Check if the current month is in the list of flow months for this account
	monthStr := anoMes[4:6]
	for _, flowMonth := range flowMonths {
		if monthStr == flowMonth {
			return true
		}
	}
	return false
}

// PromoteRawData promotes raw JSON data to normalized bank_report tables
func (r *BankReportRepository) PromoteRawData(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
	numeroRelatorio string,
	rawData []domain.IFDataRaw,
) (int, error) {
	if len(rawData) == 0 {
		return 0, nil
	}

	totalPromoted := 0

	// Group raw data by institution
	institutionMap := make(map[string][]domain.IFDataRaw)
	for _, row := range rawData {
		key := fmt.Sprintf("%s_%d", row.CodInst, row.TipoInstituicao)
		institutionMap[key] = append(institutionMap[key], row)
	}

	// Process each institution
	for key, rows := range institutionMap {
		// Parse key to get cod_inst and tipo_instituicao
		parts := strings.Split(key, "_")
		if len(parts) != 2 {
			r.logger.Warn("invalid institution key", "key", key)
			continue
		}
		codInst := parts[0]
		tipoInstituicao, _ := strconv.Atoi(parts[1])

		// Validate that institution exists in bacen_institution for this ano_mes
		// This ensures referential integrity before creating bank_report records
		checkQuery := `
			SELECT EXISTS(
				SELECT 1
				FROM financial_data.bacen_institution
				WHERE bacen_cod_inst = $1
				  AND ano_mes_ref = $2
			)
		`
		var institutionExists bool
		if err := r.pool.QueryRow(ctx, checkQuery, codInst, anoMes).Scan(&institutionExists); err != nil {
			r.logger.Warn("failed to check institution existence",
				"cod_inst", codInst,
				"ano_mes", anoMes,
				"error", err)
			continue
		}

		if !institutionExists {
			r.logger.Warn("skipping bank report - institution does not exist in bacen_institution",
				"cod_inst", codInst,
				"ano_mes", anoMes,
				"tipo_instituicao", tipoInstituicao,
				"rows_skipped", len(rows))
			continue
		}

		// Create/update inst_report (parent)
		report := &domain.InstitutionReport{
			CodInst:         codInst,
			AnoMes:          anoMes,
			NumeroRelatorio: numeroRelatorio,
			TipoInstituicao: tipoInstituicao,
		}

		if err := r.UpsertBankReport(ctx, report); err != nil {
			return totalPromoted, fmt.Errorf("failed to upsert bank report: %w", err)
		}

		// Process each raw data row
		for _, rawRow := range rows {
			// Parse JSON from raw_data
			var recordData map[string]interface{}
			if err := json.Unmarshal([]byte(rawRow.RawData), &recordData); err != nil {
				r.logger.Warn("failed to parse JSON from raw_data",
					"cod_inst", codInst,
					"error", err,
					"raw_data_preview", truncateString(rawRow.RawData, 100))
				continue
			}

			// Extract fields from JSON
			grupo := getNullableStringFromJSON(recordData, "Grupo")
			conta := getNullableStringFromJSON(recordData, "Conta")
			nomeColuna := getNullableStringFromJSON(recordData, "NomeColuna")
			descricaoColuna := getNullableStringFromJSON(recordData, "DescricaoColuna")
			saldo := getNullableFloat64FromJSON(recordData, "Saldo")

			// Truncate saldo to 4 decimal places if not nil
			if saldo != nil {
				truncated := truncateSaldo(*saldo)
				saldo = &truncated
			}

			// Get or create account (normalize account metadata)
			canonical, err := r.canonicalRepo.GetOrCreateByName(ctx, nomeColuna, conta)
			if err != nil {
				r.logger.Warn("failed to get or create canonical account",
					"cod_inst", codInst,
					"error", err)
				continue
			}

			var canonicalID *string
			var canonicalName *string
			if canonical != nil {
				canonicalID = &canonical.ID
				canonicalName = &canonical.NormalizedName
			}

			account, err := r.accountRepo.GetOrCreateAccount(
				ctx, grupo, conta, nomeColuna, descricaoColuna, canonicalID,
			)
			if err != nil {
				r.logger.Warn("failed to get or create account",
					"cod_inst", codInst,
					"error", err)
				continue
			}

			// Compute saldo_ajustado for flow accounts in their configured flow months
			var saldoAjustado *float64
			if isFlowAccountMonth(canonicalName, anoMes) && saldo != nil {
				priorSaldo, err := r.getPriorQuarterBalance(ctx, codInst, anoMes, numeroRelatorio, tipoInstituicao, account.ID)
				if err != nil {
					r.logger.Warn("failed to get prior quarter balance",
						"cod_inst", codInst,
						"ano_mes", anoMes,
						"conta", conta,
						"error", err)
					// Continue with saldo_ajustado = nil if prior quarter lookup fails
				} else if priorSaldo != nil {
					// Calculate adjusted balance: current - previous quarter
					adjusted := *saldo - *priorSaldo
					// Truncate to 4 decimal places
					truncated := truncateSaldo(adjusted)
					saldoAjustado = &truncated
				}
				// If priorSaldo is nil, saldo_ajustado remains nil (prior quarter data not found)
			}

			// Create inst_report_data (child) with account_id FK
			data := &domain.InstitutionReportData{
				InstReportID:  report.ID,
				AccountID:     account.ID,
				Saldo:         saldo,
				SaldoAjustado: saldoAjustado,
			}

			if err := r.UpsertBankReportData(ctx, data); err != nil {
				return totalPromoted, fmt.Errorf("failed to upsert bank report data: %w", err)
			}

			totalPromoted++
		}
	}

	r.logger.Info("promoted raw data to hot tables",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio,
		"numero_relatorio", numeroRelatorio,
		"institutions_processed", len(institutionMap),
		"rows_promoted", totalPromoted)

	return totalPromoted, nil
}

// getNullableStringFromJSON extracts a nullable string from JSON map
func getNullableStringFromJSON(data map[string]interface{}, key string) *string {
	val, ok := data[key]
	if !ok || val == nil {
		return nil
	}

	str, ok := val.(string)
	if !ok {
		return nil
	}

	trimmed := strings.TrimSpace(str)
	if trimmed == "" || trimmed == "null" {
		return nil
	}
	return &trimmed
}

// getNullableFloat64FromJSON extracts a nullable float64 from JSON map
func getNullableFloat64FromJSON(data map[string]interface{}, key string) *float64 {
	val, ok := data[key]
	if !ok || val == nil {
		return nil
	}

	// Handle both float64 and JSON number
	var f float64
	switch v := val.(type) {
	case float64:
		f = v
	case float32:
		f = float64(v)
	default:
		return nil
	}

	return &f
}

// truncateSaldo truncates a float64 to 4 decimal places
func truncateSaldo(value float64) float64 {
	multiplier := math.Pow(10, 4)
	truncated := math.Trunc(value*multiplier) / multiplier
	return truncated
}

// truncateString truncates a string to a maximum length for logging
func truncateString(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}

// CountPromotedRecords counts records in bank_report_data for a specific report
func (r *BankReportRepository) CountPromotedRecords(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
	numeroRelatorio string,
) (int, error) {
	query := `
		SELECT COUNT(*)
		FROM financial_data.bacen_inst_report_data brd
		INNER JOIN financial_data.bacen_inst_report br ON brd.inst_report_id = br.id
		WHERE br.ano_mes = $1
		  AND br.numero_relatorio = $2
	`

	// Note: nomeRelatorio is not used in the query as we normalized the schema,
	// but kept in signature for compatibility if needed.
	// Filtering by numero_relatorio is sufficient as it is the report ID.
	var count int
	err := r.pool.QueryRow(ctx, query, anoMes, numeroRelatorio).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("failed to count promoted records: %w", err)
	}

	return count, nil
}
