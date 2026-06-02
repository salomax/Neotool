package processor

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/application"
	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/repository"
	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/source"
	"github.com/salomax/invistus/workflow/golang/shared/workerpool"
)

// Dispatcher coordinates worker pool execution
type Dispatcher struct {
	indicatorRepo   *repository.IndicatorRepository
	loadControlRepo *repository.LoadControlRepository
	valueRepo       *repository.ValueRepository
	registry        *source.Registry
	sidraClient     *source.SIDRAClient
	sgsClient       *source.SGSClient
	comexClient     *source.ComexStatClient
	ipeadataClient  *source.IpeadataClient
	jobConfig       application.JobConfig
	filter          application.FilterConfig
	logger          *slog.Logger
}

func NewDispatcher(
	indicatorRepo *repository.IndicatorRepository,
	loadControlRepo *repository.LoadControlRepository,
	valueRepo *repository.ValueRepository,
	registry *source.Registry,
	sidraClient *source.SIDRAClient,
	sgsClient *source.SGSClient,
	comexClient *source.ComexStatClient,
	ipeadataClient *source.IpeadataClient,
	jobConfig application.JobConfig,
	filter application.FilterConfig,
	logger *slog.Logger,
) *Dispatcher {
	return &Dispatcher{
		indicatorRepo:   indicatorRepo,
		loadControlRepo: loadControlRepo,
		valueRepo:       valueRepo,
		registry:        registry,
		sidraClient:     sidraClient,
		sgsClient:       sgsClient,
		comexClient:     comexClient,
		ipeadataClient:  ipeadataClient,
		jobConfig:       jobConfig,
		filter:          filter,
		logger:          logger,
	}
}

// Dispatch loads indicator variations and runs workers
func (d *Dispatcher) Dispatch(ctx context.Context) error {
	variations, err := d.indicatorRepo.ListActiveVariations(ctx)
	if err != nil {
		return err
	}

	if len(variations) == 0 {
		d.logger.Info("no active indicator variations found")
		return nil
	}

	pool := workerpool.NewWorkerPool(d.jobConfig.MaxWorkers, d.jobConfig.QueueBufferMultiplier, d.logger)
	pool.Start(ctx, func(ctx context.Context, item workerpool.WorkItem) error {
		return item.Process(ctx)
	})

	for _, variation := range variations {
		if d.filter.IndicatorLabel != "" && variation.IndicatorLabel != d.filter.IndicatorLabel {
			continue
		}
		if d.filter.VariationLabel != "" && variation.Label != d.filter.VariationLabel {
			continue
		}
		task := NewTask(variation, d.loadControlRepo, d.valueRepo, d.registry, d.sidraClient, d.sgsClient, d.comexClient, d.ipeadataClient, d.logger)
		if err := pool.Submit(ctx, task); err != nil {
			return err
		}
	}

	pool.Shutdown()
	pool.Wait()
	if taskErrs := pool.Errors(); len(taskErrs) > 0 {
		return fmt.Errorf("%d indicator task(s) failed", len(taskErrs))
	}
	return nil
}

var _ application.Dispatcher = (*Dispatcher)(nil)
