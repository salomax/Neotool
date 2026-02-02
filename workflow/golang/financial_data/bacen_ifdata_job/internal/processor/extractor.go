package processor

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"strings"
	"sync"
	"time"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/bacen"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
)

// Extractor extracts financial data from BACEN with parallel workers
type Extractor struct {
	bacenClient     *bacen.Client
	extractTaskRepo *database.ExtractTaskRepository
	rawDataRepo     *database.RawDataRepository
	config          *domain.ExtractionConfig
	logger          *slog.Logger
}

// NewExtractor creates a new extractor
func NewExtractor(
	bacenClient *bacen.Client,
	extractTaskRepo *database.ExtractTaskRepository,
	rawDataRepo *database.RawDataRepository,
	config *domain.ExtractionConfig,
	logger *slog.Logger,
) *Extractor {
	return &Extractor{
		bacenClient:     bacenClient,
		extractTaskRepo: extractTaskRepo,
		rawDataRepo:     rawDataRepo,
		config:          config,
		logger:          logger,
	}
}

// RunExtractionWorkers runs parallel workers to extract data
func (e *Extractor) RunExtractionWorkers(ctx context.Context, workerCount int) error {
	e.logger.Info("starting extraction workers",
		"worker_count", workerCount,
		"max_iterations", e.config.MaxIterations,
		"max_task_retries", e.config.MaxTaskRetries)

	// Create worker pool
	var wg sync.WaitGroup
	errChan := make(chan error, workerCount)
	doneChan := make(chan struct{})

	// Start workers
	for i := 0; i < workerCount; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			if err := e.worker(ctx, workerID); err != nil {
				e.logger.Error("worker failed",
					"worker_id", workerID,
					"error", err)
				// Non-blocking send to prevent goroutine leak if multiple workers fail
				select {
				case errChan <- err:
				default:
					// Error channel full, another worker already reported error
					e.logger.Warn("additional worker error (channel full)",
						"worker_id", workerID,
						"error", err)
				}
			}
		}(i)
	}

	// Wait for all workers to complete
	go func() {
		wg.Wait()
		close(doneChan)
		close(errChan)
	}()

	// Wait for completion or error - collect first error if any
	var firstErr error
	for {
		select {
		case <-ctx.Done():
			// Wait for workers to finish before returning
			<-doneChan
			return ctx.Err()
		case err, ok := <-errChan:
			if ok && firstErr == nil {
				firstErr = err
			}
		case <-doneChan:
			if firstErr != nil {
				return fmt.Errorf("worker error: %w", firstErr)
			}
			e.logger.Info("all extraction workers completed")
			return nil
		}
	}
}

// worker is a single worker that processes extract tasks
func (e *Extractor) worker(ctx context.Context, workerID int) error {
	e.logger.Info("worker started", "worker_id", workerID)

	iteration := 0
	for {
		// Check context cancellation
		select {
		case <-ctx.Done():
			e.logger.Info("worker stopping due to context cancellation",
				"worker_id", workerID)
			return nil
		default:
		}

		// Safety limit
		if iteration >= e.config.MaxIterations {
			e.logger.Warn("worker reached max iterations",
				"worker_id", workerID,
				"max_iterations", e.config.MaxIterations)
			return fmt.Errorf("max iterations exceeded")
		}

		// Acquire next pending task
		task, err := e.extractTaskRepo.AcquireNextPending(ctx, e.config.MaxTaskRetries)
		if err != nil {
			return fmt.Errorf("failed to acquire task: %w", err)
		}

		if task == nil {
			// No more tasks available
			e.logger.Info("no more pending tasks, worker exiting",
				"worker_id", workerID,
				"iterations", iteration)
			return nil
		}

		// Process task
		e.logger.Info("worker processing task",
			"worker_id", workerID,
			"ano_mes", task.AnoMes,
			"tipo_instituicao", task.TipoInstituicao,
			"nome_relatorio", task.NomeRelatorio,
			"page_skip", task.PageSkip,
			"attempt_count", task.AttemptCount)

		if err := e.processTask(ctx, task); err != nil {
			e.logger.Error("failed to process task",
				"worker_id", workerID,
				"ano_mes", task.AnoMes,
				"page_skip", task.PageSkip,
				"error", err)

			// Requeue for retry or mark as error
			lastError := err.Error()
			httpStatus := bacen.GetHTTPStatusCode(err)
			var httpStatusPtr *int
			if httpStatus > 0 {
				httpStatusPtr = &httpStatus
			}

			if err := e.extractTaskRepo.RequeueForRetry(ctx, task, e.config.MaxTaskRetries, lastError, httpStatusPtr); err != nil {
				return fmt.Errorf("failed to requeue task: %w", err)
			}
		}

		iteration++
	}
}

// processTask processes a single extract task
func (e *Extractor) processTask(ctx context.Context, task *domain.ExtractTask) error {
	// Purge raw data if first page - but only if no other pages have been processed
	// This prevents deleting data that was just inserted by concurrent workers
	if task.PageSkip == 0 {
		// Check if there's any raw data for this scope
		count, err := e.rawDataRepo.CountByScope(ctx, task.AnoMes, task.TipoInstituicao, task.NomeRelatorio, task.NumeroRelatorio)
		if err != nil {
			return fmt.Errorf("failed to check existing raw data: %w", err)
		}
		
		// Only purge if there's existing data (means this is a re-run)
		// If count is 0, this is the first run and we don't need to purge
		if count > 0 {
			if err := e.rawDataRepo.PurgeByScope(
				ctx,
				task.AnoMes,
				task.TipoInstituicao,
				task.NomeRelatorio,
				task.NumeroRelatorio,
			); err != nil {
				return fmt.Errorf("failed to purge raw data: %w", err)
			}
			e.logger.Info("purged existing raw data for scope",
				"ano_mes", task.AnoMes,
				"tipo_instituicao", task.TipoInstituicao,
				"nome_relatorio", task.NomeRelatorio,
				"numero_relatorio", task.NumeroRelatorio,
				"rows_deleted", count)
		}
	}

	// Fetch data from BACEN (returns JSON)
	jsonData, err := e.bacenClient.FetchReportData(
		ctx,
		task.AnoMes,
		task.TipoInstituicao,
		task.NumeroRelatorio,
		task.PageSkip,
		task.PageTop,
	)
	if err != nil {
		return fmt.Errorf("failed to fetch report data: %w", err)
	}

	// Parse JSON response
	var response bacen.ReportDataResponse
	if err := json.Unmarshal([]byte(jsonData), &response); err != nil {
		return fmt.Errorf("failed to parse JSON response: %w", err)
	}

	e.logger.Debug("parsed JSON data",
		"ano_mes", task.AnoMes,
		"page_skip", task.PageSkip,
		"record_count", len(response.Value))

	// Check if empty
	if len(response.Value) == 0 {
		// Mark as EMPTY
		if err := e.extractTaskRepo.UpdateStatus(ctx, task, domain.StatusEmpty, nil, nil); err != nil {
			return fmt.Errorf("failed to update task status to EMPTY: %w", err)
		}
		e.logger.Info("task completed with no data",
			"ano_mes", task.AnoMes,
			"page_skip", task.PageSkip)
		return nil
	}

	// Insert rows into raw data table
	rawDataRows := make([]domain.IFDataRaw, 0, len(response.Value))
	for _, record := range response.Value {
		// Validate CodInst
		codInst := strings.TrimSpace(record.CodInst)
		if codInst == "" {
			e.logger.Warn("empty cod_inst in record",
				"ano_mes", task.AnoMes,
				"tipo_instituicao", task.TipoInstituicao)
			continue
		}

		// Store record as JSON string (only the fields we need for promotion)
		// Create a simplified record with only the fields we'll use
		simplifiedRecord := map[string]interface{}{
			"Grupo":          record.Grupo,
			"Conta":          record.Conta,
			"NomeColuna":     record.NomeColuna,
			"DescricaoColuna": record.DescricaoColuna,
			"Saldo":          record.Saldo,
		}
		
		recordJSON, err := json.Marshal(simplifiedRecord)
		if err != nil {
			e.logger.Warn("failed to marshal record to JSON",
				"cod_inst", codInst,
				"error", err)
			continue
		}

		rawDataRows = append(rawDataRows, domain.IFDataRaw{
			AnoMes:          task.AnoMes,
			TipoInstituicao: task.TipoInstituicao,
			NomeRelatorio:   task.NomeRelatorio,
			NumeroRelatorio: task.NumeroRelatorio,
			CodInst:         codInst,
			RawData:         string(recordJSON),
		})
	}

	// Insert batch
	rowsInserted, err := e.rawDataRepo.InsertBatch(ctx, rawDataRows)
	if err != nil {
		return fmt.Errorf("failed to insert raw data: %w", err)
	}

	// Update task with rows processed
	if err := e.extractTaskRepo.UpdateRowsProcessed(ctx, task, rowsInserted); err != nil {
		e.logger.Warn("failed to update rows processed",
			"error", err,
			"ano_mes", task.AnoMes,
			"page_skip", task.PageSkip)
		// Don't fail the task, just log the warning
	}

	e.logger.Info("inserted raw data",
		"rows_inserted", rowsInserted,
		"rows_processed", rowsInserted,
		"ano_mes", task.AnoMes,
		"page_skip", task.PageSkip,
		"tipo_instituicao", task.TipoInstituicao,
		"nome_relatorio", task.NomeRelatorio)

	// Mark task as SUCCESS
	if err := e.extractTaskRepo.UpdateStatus(ctx, task, domain.StatusSuccess, nil, nil); err != nil {
		return fmt.Errorf("failed to update task status to SUCCESS: %w", err)
	}

	// Check if we need to create next page task
	// Based on number of records in JSON response (not rows inserted to DB)
	// If we got a full page, there might be more data
	if len(response.Value) >= task.PageTop {
		// Check context before creating new task (graceful shutdown)
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		// Full page, create next page task
		nextPageSkip := task.PageSkip + task.PageTop
		nextTask := &domain.ExtractTask{
			AnoMes:          task.AnoMes,
			TipoInstituicao: task.TipoInstituicao,
			NomeRelatorio:   task.NomeRelatorio,
			NumeroRelatorio: task.NumeroRelatorio,
			PageSkip:        nextPageSkip,
			PageTop:         task.PageTop,
			Status:          domain.StatusPending,
			AttemptCount:    0,
		}

		if err := e.extractTaskRepo.Create(ctx, nextTask); err != nil {
			return fmt.Errorf("failed to create next page task: %w", err)
		}

		e.logger.Info("created next page task",
			"ano_mes", task.AnoMes,
			"tipo_instituicao", task.TipoInstituicao,
			"nome_relatorio", task.NomeRelatorio,
			"current_page_skip", task.PageSkip,
			"current_records", len(response.Value),
			"next_page_skip", nextPageSkip,
			"page_size", task.PageTop)
	} else {
		e.logger.Info("pagination complete - got fewer records than page size",
			"ano_mes", task.AnoMes,
			"tipo_instituicao", task.TipoInstituicao,
			"nome_relatorio", task.NomeRelatorio,
			"page_skip", task.PageSkip,
			"records_received", len(response.Value),
			"page_size", task.PageTop)
	}

	e.logger.Info("task completed successfully",
		"ano_mes", task.AnoMes,
		"tipo_instituicao", task.TipoInstituicao,
		"nome_relatorio", task.NomeRelatorio,
		"page_skip", task.PageSkip,
		"rows_inserted", rowsInserted)

	return nil
}

// WaitForCompletion polls until all extract tasks for a quarter are complete
func (e *Extractor) WaitForCompletion(ctx context.Context, anoMes string, pollInterval time.Duration) error {
	e.logger.Info("waiting for extraction completion",
		"ano_mes", anoMes,
		"poll_interval", pollInterval)

	ticker := time.NewTicker(pollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			// Check if any tasks are still pending or running
			counts, err := e.extractTaskRepo.CountByStatus(ctx, anoMes)
			if err != nil {
				return fmt.Errorf("failed to count tasks: %w", err)
			}

			pending := counts[domain.StatusPending]
			running := counts[domain.StatusRunning]

			if pending == 0 && running == 0 {
				e.logger.Info("extraction completed",
					"ano_mes", anoMes,
					"success", counts[domain.StatusSuccess],
					"empty", counts[domain.StatusEmpty],
					"error", counts[domain.StatusError])
				return nil
			}

			e.logger.Debug("extraction in progress",
				"ano_mes", anoMes,
				"pending", pending,
				"running", running,
				"success", counts[domain.StatusSuccess],
				"empty", counts[domain.StatusEmpty],
				"error", counts[domain.StatusError])
		}
	}
}
