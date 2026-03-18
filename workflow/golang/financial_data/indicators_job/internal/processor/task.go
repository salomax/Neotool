package processor

import (
	"context"
	"log/slog"
	"time"

	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/repository"
	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/source"
)

// Task represents one indicator variation load
type Task struct {
	variation       domain.IndicatorVariation
	loadControlRepo *repository.LoadControlRepository
	valueRepo       *repository.ValueRepository
	registry        *source.Registry
	sidraClient     *source.SIDRAClient
	sgsClient       *source.SGSClient
	comexClient     *source.ComexStatClient
	ipeadataClient  *source.IpeadataClient
	logger          *slog.Logger
}

func NewTask(
	variation domain.IndicatorVariation,
	loadControlRepo *repository.LoadControlRepository,
	valueRepo *repository.ValueRepository,
	registry *source.Registry,
	sidraClient *source.SIDRAClient,
	sgsClient *source.SGSClient,
	comexClient *source.ComexStatClient,
	ipeadataClient *source.IpeadataClient,
	logger *slog.Logger,
) *Task {
	return &Task{
		variation:       variation,
		loadControlRepo: loadControlRepo,
		valueRepo:       valueRepo,
		registry:        registry,
		sidraClient:     sidraClient,
		sgsClient:       sgsClient,
		comexClient:     comexClient,
		ipeadataClient:  ipeadataClient,
		logger:          logger,
	}
}

// Process executes the task (fetch/normalize/upsert)
func (t *Task) Process(ctx context.Context) error {
	logger := t.logger.With(
		"variation_id", t.variation.ID,
		"indicator", t.variation.IndicatorLabel,
		"variation", t.variation.Label,
	)
	startTime := time.Now()
	logger.Info("indicator task started")

	now := time.Now().UTC()
	lastSuccess, _, err := t.loadControlRepo.GetLastSuccess(ctx, t.variation.ID)
	if err != nil {
		logger.Error("indicator task failed", "duration_ms", time.Since(startTime).Milliseconds(), "error", err)
		return err
	}

	runType := domain.RunTypeBackfill
	if lastSuccess != nil {
		runType = domain.RunTypeIncremental
	}

	controlID, err := t.loadControlRepo.Create(ctx, &domain.LoadControl{
		IndicatorVariationID: t.variation.ID,
		RunType:              runType,
		Status:               domain.LoadStatusRunning,
		ScheduledAt:          now,
		StartedAt:            &now,
		Attempts:             1,
	})
	if err != nil {
		return err
	}

	req, err := t.registry.Resolve(t.variation, lastSuccess)
	if err != nil {
		msg := err.Error()
		_ = t.loadControlRepo.UpdateCompletion(ctx, controlID, domain.LoadStatusSkipped, nil, nil, nil, &msg)
		logger.Warn("indicator task skipped (no source mapping)",
			"duration_ms", time.Since(startTime).Milliseconds(),
			"run_type", runType,
			"error", err,
		)
		return nil
	}

	var values []domain.ValueRecord
	switch req.Source {
	case source.SourceSIDRA:
		values, err = t.sidraClient.FetchValues(ctx, t.variation.ID, req.URL)
	case source.SourceSGS:
		if err := t.sgsClient.ValidateSeries(ctx, req.MetadataURL); err != nil {
			msg := err.Error()
			_ = t.loadControlRepo.UpdateCompletion(ctx, controlID, domain.LoadStatusFailed, nil, nil, nil, &msg)
			logger.Error("indicator task failed (sgs metadata)",
				"duration_ms", time.Since(startTime).Milliseconds(),
				"run_type", runType,
				"source", req.Source,
				"error", err,
			)
			return err
		}
		values, err = t.sgsClient.FetchValues(ctx, t.variation.ID, req.URL)
	case source.SourceCOMEXSTAT:
		values, err = t.comexClient.FetchValues(ctx, t.variation.ID, req.URL, req.Body)
	case source.SourceIPEADATA:
		values, err = t.ipeadataClient.FetchValues(ctx, t.variation.ID, req.SeriesCode, req.StartDate, req.EndDate, req.CalcType)
	default:
		err = nil
	}

	if err != nil {
		msg := err.Error()
		_ = t.loadControlRepo.UpdateCompletion(ctx, controlID, domain.LoadStatusFailed, nil, nil, nil, &msg)
		logger.Error("indicator task failed",
			"duration_ms", time.Since(startTime).Milliseconds(),
			"run_type", runType,
			"source", req.Source,
			"error", err,
		)
		return err
	}

	if len(values) == 0 {
		_ = t.loadControlRepo.UpdateCompletion(ctx, controlID, domain.LoadStatusSkipped, nil, nil, nil, nil)
		logger.Info("indicator task skipped (no values)",
			"duration_ms", time.Since(startTime).Milliseconds(),
			"run_type", runType,
			"source", req.Source,
		)
		return nil
	}

	if err := t.valueRepo.UpsertValues(ctx, values); err != nil {
		msg := err.Error()
		_ = t.loadControlRepo.UpdateCompletion(ctx, controlID, domain.LoadStatusFailed, nil, nil, nil, &msg)
		logger.Error("indicator task failed (upsert)",
			"duration_ms", time.Since(startTime).Milliseconds(),
			"run_type", runType,
			"source", req.Source,
			"records", len(values),
			"error", err,
		)
		return err
	}

	lastRef := values[0].ReferenceDate
	for _, v := range values[1:] {
		if v.ReferenceDate.After(lastRef) {
			lastRef = v.ReferenceDate
		}
	}

	cursor := map[string]any{
		"last_reference_date": lastRef.Format("2006-01-02"),
		"source":              string(req.Source),
		"source_url":          req.URL,
		"metadata_url":        req.MetadataURL,
		"series_id":           req.SeriesID,
	}
	_ = t.loadControlRepo.UpdateCompletion(ctx, controlID, domain.LoadStatusSuccess, &lastRef, nil, cursor, nil)
	logger.Info("indicator task succeeded",
		"duration_ms", time.Since(startTime).Milliseconds(),
		"run_type", runType,
		"source", req.Source,
		"records", len(values),
		"last_reference_date", lastRef.Format("2006-01-02"),
	)
	return nil
}
