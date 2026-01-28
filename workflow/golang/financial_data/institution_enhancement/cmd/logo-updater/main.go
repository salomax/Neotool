package main

import (
	"context"
	"fmt"
	"log"
	"log/slog"
	"os"
	"strings"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/application"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/assets"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/database"
	graphqlClient "github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/graphql"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/logo"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/processor"
	"github.com/salomax/invistus/workflow/go/shared/auth"
)

func main() {
	// Setup logger
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	logger.Info("Starting logo updater - processing institutions with website but no logo")

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

	// Initialize repository
	repo := database.NewParentInstitutionRepository(dbPool, logger)

	// Initialize logo processor (required for this command)
	if config.LogoDev.Token == "" || config.Security.ServiceID == "" || config.Security.ServiceSecret == "" {
		log.Fatalf("Logo processor configuration is required: LOGO_DEV_TOKEN, SECURITY_SERVICE_ID, and SECURITY_SERVICE_SECRET must be set")
	}

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
	logoProc := processor.NewLogoProcessor(logoDownloader, assetUploader, logger)

	// Find all institutions with website but no asset_id
	institutions, err := repo.FindInstitutionsWithoutLogo(ctx)
	if err != nil {
		log.Fatalf("Failed to find institutions without logo: %v", err)
	}

	if len(institutions) == 0 {
		logger.Info("No institutions found that need logo processing")
		fmt.Println("\nNo institutions found with website but without logo. All done!")
		return
	}

	fmt.Printf("\nFound %d institutions to process\n\n", len(institutions))

	// Process each institution
	successCount := 0
	failureCount := 0
	skippedCount := 0

	for i, inst := range institutions {
		logger.Info("Processing institution",
			"index", i+1,
			"total", len(institutions),
			"id", inst.ID,
			"parent_cnpj", inst.ParentCNPJ,
			"website", inst.Website)

		// Process logo
		assetID := logoProc.ProcessLogo(ctx, &inst.Website)

		if assetID == nil {
			logger.Warn("Logo processing returned nil - skipping update",
				"parent_cnpj", inst.ParentCNPJ,
				"website", inst.Website)
			skippedCount++
			continue
		}

		// Update asset_id in database
		if err := repo.UpdateAssetID(ctx, inst.ID, *assetID); err != nil {
			logger.Error("Failed to update asset_id",
				"id", inst.ID,
				"parent_cnpj", inst.ParentCNPJ,
				"error", err)
			failureCount++
			continue
		}

		successCount++
		fmt.Printf("[%d/%d] Updated logo for %s (website: %s, asset_id: %s)\n",
			i+1, len(institutions), inst.ParentCNPJ, inst.Website, *assetID)
	}

	// Print summary
	fmt.Println("\n" + strings.Repeat("=", 60))
	fmt.Println("Logo Update Summary")
	fmt.Println(strings.Repeat("=", 60))
	fmt.Printf("Total institutions processed: %d\n", len(institutions))
	fmt.Printf("  - Successfully updated: %d\n", successCount)
	fmt.Printf("  - Skipped (no logo found): %d\n", skippedCount)
	fmt.Printf("  - Failed: %d\n", failureCount)
	fmt.Println(strings.Repeat("=", 60))

	if failureCount > 0 {
		os.Exit(1)
	}
}
