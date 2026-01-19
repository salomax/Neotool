package processor

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
)

// ExtractTaskCreator creates extract tasks for reports
type ExtractTaskCreator struct {
	extractTaskRepo *database.ExtractTaskRepository
	logger          *slog.Logger
}

// NewExtractTaskCreator creates a new extract task creator
func NewExtractTaskCreator(
	extractTaskRepo *database.ExtractTaskRepository,
	logger *slog.Logger,
) *ExtractTaskCreator {
	return &ExtractTaskCreator{
		extractTaskRepo: extractTaskRepo,
		logger:          logger,
	}
}

// CreateTasks creates extract tasks for all reports and institution types
// Each task represents one page of data to extract
func (etc *ExtractTaskCreator) CreateTasks(
	ctx context.Context,
	anoMes string,
	reports []domain.Report,
	pageSize int,
) (int, error) {
	etc.logger.Info("creating extract tasks",
		"ano_mes", anoMes,
		"report_count", len(reports),
		"page_size", pageSize)

	tasksCreated := 0

	// Institution types: 1, 2, 3, 4
	institutionTypes := []int{1, 2, 3, 4}

	for _, report := range reports {
		for _, tipoInstituicao := range institutionTypes {
			// Create initial task (page 0)
			task := &domain.ExtractTask{
				AnoMes:          anoMes,
				TipoInstituicao: tipoInstituicao,
				NomeRelatorio:   report.NomeRelatorio,
				NumeroRelatorio: report.NumeroRelatorio,
				PageSkip:        0,
				PageTop:         pageSize,
				Status:          domain.StatusPending,
				AttemptCount:    0,
			}

			if err := etc.extractTaskRepo.Create(ctx, task); err != nil {
				return tasksCreated, fmt.Errorf("failed to create task: %w", err)
			}

			tasksCreated++
		}
	}

	etc.logger.Info("created extract tasks",
		"ano_mes", anoMes,
		"tasks_created", tasksCreated,
		"reports", len(reports),
		"institution_types", len(institutionTypes))

	return tasksCreated, nil
}
