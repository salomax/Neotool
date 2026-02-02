package domain

import "context"

// BACENClient defines the interface for BACEN API client
type BACENClient interface {
	// FetchRegistry fetches institution registry for a specific quarter
	FetchRegistry(ctx context.Context, anoMes string) (string, error)

	// FetchReportCatalog fetches the list of available reports
	FetchReportCatalog(ctx context.Context) (string, error)

	// FetchReportData fetches financial data for a specific report with pagination
	FetchReportData(ctx context.Context, anoMes string, tipoInstituicao int, numeroRelatorio string, pageSkip, pageTop int) (string, error)
}

// InstitutionRepository defines the interface for institution data persistence
type InstitutionRepository interface {
	// Upsert inserts or updates an institution
	Upsert(ctx context.Context, inst *Institution) error

	// FindByBacenCodInst retrieves an institution by its code
	FindByBacenCodInst(ctx context.Context, bacenCodInst string) (*Institution, error)

	// CountByAnoMes counts institutions for a specific quarter
	CountByAnoMes(ctx context.Context, anoMes string) (int, error)

	// GetLastExtractedAnoMes retrieves the last extracted ano_mes from load control
	GetLastExtractedAnoMes(ctx context.Context) (string, error)
}

// ExtractTaskRepository defines the interface for extract task persistence
type ExtractTaskRepository interface {
	// Create creates a new extract task (idempotent)
	Create(ctx context.Context, task *ExtractTask) error

	// AcquireNextPending acquires the next pending task with row-level locking
	AcquireNextPending(ctx context.Context, maxRetries int) (*ExtractTask, error)

	// UpdateStatus updates the status of an extract task
	UpdateStatus(ctx context.Context, taskID int, status ExtractTaskStatus, lastError *string, lastHTTPStatus *int) error

	// RequeueForRetry requeues a task for retry if under max attempts
	RequeueForRetry(ctx context.Context, taskID int, maxRetries int, lastError string, lastHTTPStatus *int) error

	// IsExtractionComplete checks if all extract tasks for a report are complete
	IsExtractionComplete(ctx context.Context, anoMes, nomeRelatorio, numeroRelatorio string) (bool, error)

	// CountByStatus counts tasks by status for a given quarter
	CountByStatus(ctx context.Context, anoMes string) (map[ExtractTaskStatus]int, error)
}

// RawDataRepository defines the interface for raw data persistence
type RawDataRepository interface {
	// InsertBatch inserts a batch of raw data rows
	InsertBatch(ctx context.Context, rows []IFDataRaw) (int, error)

	// PurgeByScope deletes raw data for a specific scope (report + institution type)
	PurgeByScope(ctx context.Context, anoMes string, tipoInstituicao int, nomeRelatorio, numeroRelatorio string) error

	// GetByReport retrieves raw data for a specific report
	GetByReport(ctx context.Context, anoMes, nomeRelatorio, numeroRelatorio string) ([]IFDataRaw, error)
}

// BankReportRepository defines the interface for normalized bank report persistence
type BankReportRepository interface {
	// UpsertReport upserts a bank report (parent) and its data rows (children)
	UpsertReport(ctx context.Context, report *InstitutionReport, dataRows []InstitutionReportData) error

	// DeleteByScope deletes reports for a specific scope
	DeleteByScope(ctx context.Context, anoMes, nomeRelatorio, numeroRelatorio string) error

	// CountByAnoMes counts reports for a specific quarter
	CountByAnoMes(ctx context.Context, anoMes string) (int, error)
}

// LoadControlRepository defines the interface for load control persistence
type LoadControlRepository interface {
	// Upsert inserts or updates load control record
	Upsert(ctx context.Context, loadControl *LoadControl) error

	// GetByAnoMesAndReport retrieves load control for a specific report
	GetByAnoMesAndReport(ctx context.Context, anoMes, nomeRelatorio string) (*LoadControl, error)

	// MarkExtracted marks extraction as complete for a report
	MarkExtracted(ctx context.Context, anoMes, nomeRelatorio string) error

	// MarkPromoted marks promotion as complete for a report
	MarkPromoted(ctx context.Context, anoMes, nomeRelatorio string) error

	// MarkCompleted marks the entire load as complete for a report
	MarkCompleted(ctx context.Context, anoMes, nomeRelatorio string) error

	// GetStatsByAnoMes retrieves aggregated stats for a quarter
	GetStatsByAnoMes(ctx context.Context, anoMes string) (map[string]interface{}, error)
}

// KafkaPublisher defines the interface for Kafka event publishing
type KafkaPublisher interface {
	// PublishChanges publishes institution changes to Kafka
	PublishChanges(ctx context.Context, changes []InstitutionChange) (published int, failed int, err error)

	// Close closes the Kafka producer
	Close() error
}
