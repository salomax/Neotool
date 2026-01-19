package main

import (
	"context"
	"fmt"
	"log"
	"log/slog"
	"os"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/application"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/domain"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/assets"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/cnpja"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/database"
	graphqlClient "github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/graphql"
	httpClient "github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/http"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/logo"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/serper"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/websites"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/processor"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/shared"
	"github.com/salomax/invistus/workflow/go/shared/auth"
)

func main() {
	// Setup logger
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	// Check command-line arguments
	if len(os.Args) < 2 {
		fmt.Println("Usage: institution-enhancement-standalone <CNPJ>")
		fmt.Println("Example: institution-enhancement-standalone 12345678000190")
		os.Exit(1)
	}

	cnpj := os.Args[1]

	// Validate CNPJ format
	if !domain.ValidateCNPJFormat(cnpj) {
		fmt.Printf("Error: Invalid CNPJ format: %s (must be exactly 14 digits)\n", cnpj)
		os.Exit(1)
	}

	logger.Info("Processing CNPJ", "cnpj", cnpj)

	// Load configuration
	config, err := application.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Create context
	ctx := context.Background()

	// Initialize PostgreSQL connection pool
	dbPool, err := database.NewConnectionPool(ctx, config.Database.ConnectionString(), logger)
	if err != nil {
		log.Fatalf("Failed to create database connection pool: %v", err)
	}
	defer dbPool.Close()

	// Initialize CNPJá client
	cnpjaClient := cnpja.NewClient(
		config.CNPJA.BaseURL,
		config.CNPJA.Timeout,
		config.CNPJA.RateLimitDelay,
		logger,
	)

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

	// Create minimal InstitutionUpsertedMessage
	now := time.Now().UTC().Format(time.RFC3339)
	msg := &domain.InstitutionUpsertedMessage{
		SchemaVersion:  "1.0",
		EventID:        fmt.Sprintf("standalone-%d", time.Now().Unix()),
		OccurredAt:     now,
		Source:         "STANDALONE",
		BacenCodInst:   "",  // Will use provided CNPJ as parent_cnpj directly
		ChangeType:     "UPDATED",
		CnpjInstituicaoLider: &cnpj, // Use provided CNPJ
	}

	// Process message
	if err := proc.ProcessMessage(ctx, msg); err != nil {
		fmt.Printf("Error: Failed to process CNPJ %s: %v\n", cnpj, err)
		os.Exit(1)
	}

	// Query result from database
	institution, err := repo.FindByParentCNPJ(ctx, cnpj)
	if err != nil {
		fmt.Printf("Error: Failed to query result: %v\n", err)
		os.Exit(1)
	}

	// Print results
	fmt.Println("\nSuccessfully processed CNPJ:", cnpj)
	if institution.TradeName != nil {
		fmt.Println("  Trade Name:", *institution.TradeName)
	}
	if institution.LegalName != nil {
		fmt.Println("  Legal Name:", *institution.LegalName)
	}
	if institution.Website != nil {
		fmt.Println("  Website:", *institution.Website)
	}
	if institution.EmailDomain != nil {
		fmt.Println("  Domain:", *institution.EmailDomain)
	}
	if len(institution.SocialNetwork) > 0 {
		fmt.Println("  Social Networks:")
		for network, url := range institution.SocialNetwork {
			fmt.Printf("    %s: %s\n", network, url)
		}
	}

	fmt.Println("\nProcessing complete!")
}
