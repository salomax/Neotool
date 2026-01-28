package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/application"
	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/infrastructure/database"
	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/processor"
	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/repository"
	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/source"
	sharedhealth "github.com/salomax/invistus/workflow/go/shared/health"
)

func main() {
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(logger)

	logger.Info("starting indicators cronjob")

	cfg, err := application.LoadConfig()
	if err != nil {
		logger.Error("failed to load config", "error", err)
		os.Exit(1)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigCh
		logger.Info("received signal, shutting down", "signal", sig)
		cancel()
	}()

	pool, err := database.NewConnectionPool(ctx, cfg.Database.ConnectionString(), logger)
	if err != nil {
		logger.Error("failed to connect to database", "error", err)
		os.Exit(1)
	}
	defer pool.Close()

	var healthServer *sharedhealth.HealthServer
	if cfg.HealthCheck.Enabled {
		checks := []sharedhealth.HealthCheck{
			sharedhealth.NewDatabaseHealthCheck(pool, logger),
		}
		healthServer = sharedhealth.NewHealthServer(cfg.HealthCheck.Port, checks, logger)
		if err := healthServer.Start(ctx); err != nil {
			logger.Error("failed to start health server", "error", err)
			os.Exit(1)
		}
		healthServer.SetLive(true)
		healthServer.SetReady(true)
		defer func() {
			shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer shutdownCancel()
			_ = healthServer.Shutdown(shutdownCtx)
		}()
	}

	indicatorRepo := repository.NewIndicatorRepository(pool, logger)
	loadControlRepo := repository.NewLoadControlRepository(pool, logger)
	valueRepo := repository.NewValueRepository(pool, logger)

	registry := source.NewRegistry(cfg.Job.BackfillMonths, cfg.Job.BackfillYears, cfg.Job.IncrementalMonths, cfg.Job.IncrementalYears, cfg.Job.IncrementalDays)
	sidraClient := source.NewSIDRAClient(cfg.Sources.SIDRATimeout)
	sgsClient := source.NewSGSClient(cfg.Sources.SGSTimeout, cfg.Sources.SGSMinInterval)
	ipeadataClient := source.NewIpeadataClient(cfg.Sources.IPEADATATimeout, cfg.Sources.IPEADATABaseURL)
	comexHeaders := map[string]string{
		"User-Agent": cfg.Sources.COMEXSTATUserAgent,
		"Referer":    cfg.Sources.COMEXSTATReferer,
		"Origin":     cfg.Sources.COMEXSTATOrigin,
		"Accept":     cfg.Sources.COMEXSTATAccept,
	}
	comexClient := source.NewComexStatClient(
		cfg.Sources.COMEXSTATTimeout,
		cfg.Sources.COMEXSTATMinInterval,
		cfg.Sources.COMEXSTATMaxRetries,
		cfg.Sources.COMEXSTATRetryWait,
		comexHeaders,
	)

	dispatcher := processor.NewDispatcher(
		indicatorRepo,
		loadControlRepo,
		valueRepo,
		registry,
		sidraClient,
		sgsClient,
		comexClient,
		ipeadataClient,
		cfg.Job,
		cfg.Filters,
		logger,
	)

	runner := application.NewCronjobRunner(dispatcher, logger)
	if err := runner.Run(ctx); err != nil {
		logger.Error("indicators cronjob failed", "error", err)
		os.Exit(1)
	}

	logger.Info("indicators cronjob completed")
}
