package processor

import (
	"context"
	"fmt"
	"log/slog"
	"sync"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
)

// Promoter promotes raw data to hot tables
type Promoter struct {
	rawDataRepo     *database.RawDataRepository
	bankReportRepo  *database.BankReportRepository
	loadControlRepo *database.LoadControlRepository
	extractTaskRepo *database.ExtractTaskRepository
	logger          *slog.Logger
}

// NewPromoter creates a new promoter
func NewPromoter(
	rawDataRepo *database.RawDataRepository,
	bankReportRepo *database.BankReportRepository,
	loadControlRepo *database.LoadControlRepository,
	extractTaskRepo *database.ExtractTaskRepository,
	logger *slog.Logger,
) *Promoter {
	return &Promoter{
		rawDataRepo:     rawDataRepo,
		bankReportRepo:  bankReportRepo,
		loadControlRepo: loadControlRepo,
		extractTaskRepo: extractTaskRepo,
		logger:          logger,
	}
}

// PromoteReport promotes raw data for a specific report to hot tables
func (p *Promoter) PromoteReport(
	ctx context.Context,
	anoMes string,
	nomeRelatorio string,
	numeroRelatorio string,
) (int, error) {
	p.logger.Info("promoting report to hot tables",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio,
		"numero_relatorio", numeroRelatorio)

	// Check if extraction is complete for this report
	isComplete, err := p.extractTaskRepo.IsExtractionComplete(ctx, anoMes, nomeRelatorio, numeroRelatorio)
	if err != nil {
		return 0, fmt.Errorf("failed to check extraction completion: %w", err)
	}

	if !isComplete {
		p.logger.Warn("extraction not complete, skipping promotion",
			"ano_mes", anoMes,
			"nome_relatorio", nomeRelatorio,
			"numero_relatorio", numeroRelatorio)
		return 0, fmt.Errorf("extraction not complete for report %s/%s", nomeRelatorio, numeroRelatorio)
	}

	// Initialize load control record
	_, err = p.loadControlRepo.InitializeLoadControl(ctx, anoMes, nomeRelatorio)
	if err != nil {
		return 0, fmt.Errorf("failed to initialize load control: %w", err)
	}

	// Get raw data for promotion (only active institutions)
	rawData, err := p.rawDataRepo.GetRawDataForPromotion(ctx, anoMes, nomeRelatorio, numeroRelatorio)
	if err != nil {
		return 0, fmt.Errorf("failed to get raw data: %w", err)
	}

	if len(rawData) == 0 {
		p.logger.Info("no raw data to promote",
			"ano_mes", anoMes,
			"nome_relatorio", nomeRelatorio,
			"numero_relatorio", numeroRelatorio)

		// Mark as extracted and promoted
		if err := p.loadControlRepo.MarkExtracted(ctx, anoMes, nomeRelatorio); err != nil {
			return 0, fmt.Errorf("failed to mark extracted: %w", err)
		}
		if err := p.loadControlRepo.MarkPromoted(ctx, anoMes, nomeRelatorio); err != nil {
			return 0, fmt.Errorf("failed to mark promoted: %w", err)
		}

		return 0, nil
	}

	p.logger.Info("promoting raw data",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio,
		"numero_relatorio", numeroRelatorio,
		"raw_rows", len(rawData))

	// Promote to bank_report and bank_report_data tables
	rowsPromoted, err := p.bankReportRepo.PromoteRawData(ctx, anoMes, nomeRelatorio, numeroRelatorio, rawData)
	if err != nil {
		return 0, fmt.Errorf("failed to promote raw data: %w", err)
	}

	// Mark as extracted and promoted
	if err := p.loadControlRepo.MarkExtracted(ctx, anoMes, nomeRelatorio); err != nil {
		return 0, fmt.Errorf("failed to mark extracted: %w", err)
	}
	if err := p.loadControlRepo.MarkPromoted(ctx, anoMes, nomeRelatorio); err != nil {
		return 0, fmt.Errorf("failed to mark promoted: %w", err)
	}

	p.logger.Info("successfully promoted report",
		"ano_mes", anoMes,
		"nome_relatorio", nomeRelatorio,
		"numero_relatorio", numeroRelatorio,
		"rows_promoted", rowsPromoted)

	return rowsPromoted, nil
}

// PromoteAllReports promotes all reports for a quarter
// If workerCount > 1, reports are promoted in parallel using goroutines
func (p *Promoter) PromoteAllReports(ctx context.Context, anoMes string, reports []domain.Report, workerCount int) (int, error) {
	p.logger.Info("promoting all reports for quarter",
		"ano_mes", anoMes,
		"report_count", len(reports),
		"worker_count", workerCount)

	if workerCount <= 1 || len(reports) <= 1 {
		// Sequential processing for single worker or single report
		return p.promoteAllReportsSequential(ctx, anoMes, reports)
	}

	// Parallel processing with worker pool
	return p.promoteAllReportsParallel(ctx, anoMes, reports, workerCount)
}

// promoteAllReportsSequential promotes reports sequentially (original implementation)
func (p *Promoter) promoteAllReportsSequential(ctx context.Context, anoMes string, reports []domain.Report) (int, error) {
	totalPromoted := 0

	for _, report := range reports {
		promoted, err := p.PromoteReport(ctx, anoMes, report.NomeRelatorio, report.NumeroRelatorio)
		if err != nil {
			// Log error but continue with other reports
			p.logger.Error("failed to promote report",
				"ano_mes", anoMes,
				"nome_relatorio", report.NomeRelatorio,
				"numero_relatorio", report.NumeroRelatorio,
				"error", err)
			continue
		}

		totalPromoted += promoted
	}

	// Mark quarter as completed
	if err := p.loadControlRepo.MarkQuarterCompleted(ctx, anoMes); err != nil {
		return totalPromoted, fmt.Errorf("failed to mark quarter completed: %w", err)
	}

	p.logger.Info("completed promotion for all reports",
		"ano_mes", anoMes,
		"total_rows_promoted", totalPromoted)

	return totalPromoted, nil
}

// promotionResult holds the result of promoting a single report
type promotionResult struct {
	nomeRelatorio  string
	numeroRelatorio string
	rowsPromoted   int
	err            error
}

// promoteAllReportsParallel promotes reports in parallel using a worker pool
func (p *Promoter) promoteAllReportsParallel(ctx context.Context, anoMes string, reports []domain.Report, workerCount int) (int, error) {
	// Create channel for reports
	reportChan := make(chan domain.Report, len(reports))
	for _, report := range reports {
		reportChan <- report
	}
	close(reportChan)

	// Create worker pool
	var wg sync.WaitGroup
	resultChan := make(chan promotionResult, len(reports))
	errChan := make(chan error, workerCount)

	// Start workers
	for i := 0; i < workerCount; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			p.promotionWorker(ctx, workerID, anoMes, reportChan, resultChan, errChan)
		}(i)
	}

	// Wait for all workers to complete
	go func() {
		wg.Wait()
		close(resultChan)
		close(errChan)
	}()

	// Collect results
	totalPromoted := 0
	var firstErr error
	resultsCollected := 0

	for {
		select {
		case <-ctx.Done():
			return totalPromoted, ctx.Err()
		case result, ok := <-resultChan:
			if !ok {
				// All results collected
				if firstErr != nil {
					return totalPromoted, fmt.Errorf("promotion error: %w", firstErr)
				}

				// Mark quarter as completed
				if err := p.loadControlRepo.MarkQuarterCompleted(ctx, anoMes); err != nil {
					return totalPromoted, fmt.Errorf("failed to mark quarter completed: %w", err)
				}

				p.logger.Info("completed promotion for all reports",
					"ano_mes", anoMes,
					"total_rows_promoted", totalPromoted,
					"reports_processed", resultsCollected)

				return totalPromoted, nil
			}
			totalPromoted += result.rowsPromoted
			resultsCollected++
			if result.err != nil && firstErr == nil {
				firstErr = result.err
				p.logger.Error("promotion worker error",
					"ano_mes", anoMes,
					"nome_relatorio", result.nomeRelatorio,
					"numero_relatorio", result.numeroRelatorio,
					"error", result.err)
			}
		case err, ok := <-errChan:
			if ok && firstErr == nil {
				firstErr = err
			}
		}
	}
}

// promotionWorker is a single worker that processes reports from the channel
func (p *Promoter) promotionWorker(
	ctx context.Context,
	workerID int,
	anoMes string,
	reportChan <-chan domain.Report,
	resultChan chan<- promotionResult,
	errChan chan<- error,
) {
	p.logger.Debug("promotion worker started", "worker_id", workerID)

	for report := range reportChan {
		// Check context cancellation
		select {
		case <-ctx.Done():
			errChan <- ctx.Err()
			return
		default:
		}

		rowsPromoted, err := p.PromoteReport(ctx, anoMes, report.NomeRelatorio, report.NumeroRelatorio)

		resultChan <- promotionResult{
			nomeRelatorio:  report.NomeRelatorio,
			numeroRelatorio: report.NumeroRelatorio,
			rowsPromoted:   rowsPromoted,
			err:            err,
		}

		if err != nil {
			p.logger.Warn("promotion worker failed to promote report",
				"worker_id", workerID,
				"ano_mes", anoMes,
				"nome_relatorio", report.NomeRelatorio,
				"numero_relatorio", report.NumeroRelatorio,
				"error", err)
			// Continue processing other reports
		} else {
			p.logger.Debug("promotion worker completed report",
				"worker_id", workerID,
				"nome_relatorio", report.NomeRelatorio,
				"rows_promoted", rowsPromoted)
		}
	}

	p.logger.Debug("promotion worker finished", "worker_id", workerID)
}

// GetPromotionStats returns promotion statistics for a quarter
func (p *Promoter) GetPromotionStats(ctx context.Context, anoMes string) (map[string]int, error) {
	return p.loadControlRepo.GetCompletionStats(ctx, anoMes)
}
