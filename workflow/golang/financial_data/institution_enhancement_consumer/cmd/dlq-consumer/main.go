package main

import (
	"context"
	"log"
	"log/slog"
	"os"
	"time"

	"fmt"

	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/application"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/assets"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/cnpja"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/database"
	graphqlClient "github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/graphql"
	httpClient "github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/http"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/kafka"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/logo"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/serper"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/websites"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/processor"
	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/shared"
	"github.com/salomax/invistus/workflow/golang/shared/auth"
)

func main() {
	// Setup logger
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	logger.Info("Starting institution enhancement DLQ consumer")

	// Load configuration
	config, err := application.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	logger.Info("Configuration loaded successfully")

	// Create context
	ctx := context.Background()

	// Initialize PostgreSQL connection pool
	dbPool, err := database.NewConnectionPool(ctx, config.Database.ConnectionString(), logger)
	if err != nil {
		log.Fatalf("Failed to create database connection pool: %v", err)
	}
	defer dbPool.Close()

	logger.Info("Database connection pool created")

	// Initialize Kafka consumer group for DLQ
	dlqConsumerGroup, err := kafka.NewConsumerGroup(
		config.Kafka.BrokerList(),
		config.Kafka.ConsumerGroup+"-dlq", // Separate consumer group for DLQ
		logger,
	)
	if err != nil {
		log.Fatalf("Failed to create Kafka DLQ consumer group: %v", err)
	}
	defer dlqConsumerGroup.Close()

	logger.Info("Kafka DLQ consumer group created")

	// Initialize Kafka producer (for re-publishing to DLQ if retry fails)
	producer, err := kafka.NewSyncProducer(config.Kafka.BrokerList(), logger)
	if err != nil {
		log.Fatalf("Failed to create Kafka producer: %v", err)
	}
	defer producer.Close()

	logger.Info("Kafka producer created")

	// Initialize DLQ publisher
	dlqPublisher := kafka.NewDLQPublisher(producer, config.Kafka.DLQTopic, logger)

	// Initialize CNPJá client
	cnpjaClient := cnpja.NewClient(
		config.CNPJA.BaseURL,
		config.CNPJA.Timeout,
		config.CNPJA.RateLimitDelay,
		logger,
	)

	logger.Info("CNPJá client created")

	// Initialize HTTP client with retry policy: 2 retries (30s, 60s)
	websiteRetryPolicy := shared.RetryPolicy{
		MaxRetries:        1, // 0-indexed: 0, 1 = 2 total attempts
		InitialDelay:      30 * time.Second,
		MaxDelay:          60 * time.Second,
		BackoffMultiplier: 2.0, // 30s → 60s
		Jitter:            false,
	}
	httpCli := httpClient.NewClient(
		20*time.Second,
		websiteRetryPolicy,
		logger,
		httpClient.WithInsecureSkipVerify(true), // For Brazilian websites with cert issues
	)

	// Initialize scraper
	scraper := httpClient.NewScraper(httpCli, logger)

	// Initialize Serper client (optional)
	var serperCli *serper.Client
	if config.Serper.Enabled && config.Serper.APIKey != "" {
		serperCli = serper.NewClient(config.Serper.APIKey, config.Serper.Timeout, logger)
		logger.Info("Serper API enabled for website discovery")
	} else {
		logger.Info("Serper API disabled")
	}

	// Initialize CSV lookup service (optional)
	var csvLookup *websites.CSVLookup
	if config.WebsitesCSV.Enabled {
		var err error
		csvLookup, err = websites.NewCSVLookup(config.WebsitesCSV.FilePath, logger)
		if err != nil {
			logger.Warn("Failed to initialize CSV lookup service, continuing without it",
				"error", err.Error(),
				"file_path", config.WebsitesCSV.FilePath)
			// Continue without CSV lookup (graceful degradation)
		} else {
			logger.Info("CSV lookup service enabled")
		}
	} else {
		logger.Info("CSV lookup service disabled")
	}

	// Initialize repository
	repo := database.NewParentInstitutionRepository(dbPool, logger)

	// Initialize processors
	websiteProc := processor.NewWebsiteProcessor(httpCli, serperCli, csvLookup, logger)
	socialNetworkProc := processor.NewSocialNetworkProcessor(scraper, logger)

	// Initialize logo processor (optional - only if configured)
	var logoProc *processor.LogoProcessor
	if config.LogoDev.Token != "" && config.Security.ServiceID != "" && config.Security.ServiceSecret != "" {
		// Initialize logo downloader
		logoDownloader := logo.NewClient(
			config.LogoDev.Token,
			time.Duration(config.LogoDev.Timeout)*time.Second,
			logger,
		)

		// Initialize service token client for OAuth2 client credentials flow
		securityURL := fmt.Sprintf("http://%s", config.Security.Host)
		tokenClient := auth.NewTokenClient(
			securityURL,
			config.Security.ServiceID,
			config.Security.ServiceSecret,
			"assets", // Audience for assets service
			time.Duration(config.Security.Timeout)*time.Second,
			logger,
		)

		// Initialize GraphQL client (token will be fetched dynamically)
		graphqlCli := graphqlClient.NewClient(
			config.Assets.ApolloRouterURL,
			"", // Token will be provided per-request via TokenClient
			time.Duration(config.Assets.GraphQLTimeout)*time.Second,
			logger,
		)

		// Initialize asset uploader
		assetUploader := assets.NewClient(
			graphqlCli,
			tokenClient,
			time.Duration(config.Assets.UploadTimeout)*time.Second,
			config.Assets.Namespace,
			logger,
		)

		// Initialize logo processor
		logoProc = processor.NewLogoProcessor(logoDownloader, assetUploader, logger)
		logger.Info("Logo processor enabled")
	} else {
		logger.Info("Logo processor disabled (missing LOGO_DEV_TOKEN, SECURITY_SERVICE_ID, or SECURITY_SERVICE_SECRET)")
	}

	// Initialize main processor
	proc := processor.NewProcessor(
		cnpjaClient,
		repo,
		websiteProc,
		socialNetworkProc,
		logoProc,
		logger,
	)

	logger.Info("Processors initialized")

	// Initialize health server
	var healthServer *application.HealthServer
	if config.HealthCheck.Enabled {
		healthServer = application.NewHealthServer(
			config.HealthCheck.Port,
			dbPool,
			dlqConsumerGroup,
			logger,
		)
	}

	// Initialize DLQ consumer runner
	runner := application.NewDLQConsumerRunner(
		config,
		dlqConsumerGroup,
		proc,
		producer,
		dlqPublisher,
		healthServer,
		logger,
	)

	logger.Info("DLQ consumer runner initialized")

	// Run DLQ consumer
	if err := runner.Run(ctx); err != nil {
		log.Fatalf("DLQ consumer runner failed: %v", err)
	}

	logger.Info("DLQ consumer shutdown complete")
}
