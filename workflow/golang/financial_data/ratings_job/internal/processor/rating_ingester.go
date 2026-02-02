package processor

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/invistus/ratings/internal/domain"
)

// RatingIngester processes ratings for all enabled institutions
type RatingIngester struct {
	issuerRepo      domain.IssuerRepository
	strategyFactory IssuerStrategyFactory
	registrar       RatingRegistrar
	logger          *slog.Logger
}

// NewRatingIngester creates a new ingester
func NewRatingIngester(
	issuerRepo domain.IssuerRepository,
	strategyFactory IssuerStrategyFactory,
	registrar RatingRegistrar,
	logger *slog.Logger,
) *RatingIngester {
	return &RatingIngester{
		issuerRepo:      issuerRepo,
		strategyFactory: strategyFactory,
		registrar:       registrar,
		logger:          logger.With("processor", "rating_ingester"),
	}
}

// ProcessAll processes ratings for all enabled issuers and institutions
func (ri *RatingIngester) ProcessAll(ctx context.Context) (*domain.IngestionStats, error) {
	stats := &domain.IngestionStats{}
	startTime := time.Now()

	// Get enabled issuers
	issuers, err := ri.issuerRepo.GetEnabled(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get enabled issuers: %w", err)
	}

	if len(issuers) == 0 {
		ri.logger.Warn("no enabled issuers found")
		return stats, nil
	}

	ri.logger.Info("starting rating ingestion", "issuer_count", len(issuers))

	for _, issuer := range issuers {
		issuerStats, err := ri.processIssuer(ctx, issuer)
		if err != nil {
			ri.logger.Error("failed to process issuer",
				"issuer", issuer.Code,
				"error", err,
			)
			stats.Errors++
			continue
		}

		// Aggregate stats
		stats.IssuersProcessed++
		stats.InstitutionsProcessed += issuerStats.InstitutionsProcessed
		stats.RatingsCreated += issuerStats.RatingsCreated
		stats.RatingsUpdated += issuerStats.RatingsUpdated
		stats.RatingsUnchanged += issuerStats.RatingsUnchanged
		stats.EvidenceStored += issuerStats.EvidenceStored
		stats.Errors += issuerStats.Errors
	}

	stats.Duration = time.Since(startTime)

	ri.logger.Info("rating ingestion completed",
		"issuers_processed", stats.IssuersProcessed,
		"institutions_processed", stats.InstitutionsProcessed,
		"ratings_created", stats.RatingsCreated,
		"ratings_updated", stats.RatingsUpdated,
		"ratings_unchanged", stats.RatingsUnchanged,
		"errors", stats.Errors,
		"duration", stats.Duration,
	)

	return stats, nil
}

// ProcessIssuer processes all institutions for a specific issuer
func (ri *RatingIngester) ProcessIssuer(ctx context.Context, issuerCode string) (*domain.IngestionStats, error) {
	issuer, err := ri.issuerRepo.GetByCode(ctx, issuerCode)
	if err != nil {
		return nil, fmt.Errorf("failed to get issuer: %w", err)
	}

	return ri.processIssuer(ctx, *issuer)
}

// processIssuer processes all institutions for a single issuer
func (ri *RatingIngester) processIssuer(ctx context.Context, issuer domain.RatingIssuer) (*domain.IngestionStats, error) {
	stats := &domain.IngestionStats{}

	ri.logger.Info("processing issuer",
		"issuer", issuer.Code,
		"display_name", issuer.DisplayName,
	)

	// Check if strategy supports this issuer
	if !ri.strategyFactory.SupportsIssuer(issuer.Code) {
		ri.logger.Warn("issuer not supported by strategy factory",
			"issuer", issuer.Code,
		)
		return stats, nil
	}

	// Create strategy for this issuer
	strategy, err := ri.strategyFactory.CreateStrategy(issuer)
	if err != nil {
		return nil, fmt.Errorf("failed to create strategy: %w", err)
	}

	// Get mappings for this issuer
	mappings, mappingErrors, err := strategy.ListMappings(ctx, issuer)
	if err != nil {
		return nil, fmt.Errorf("failed to get mappings: %w", err)
	}
	stats.Errors += mappingErrors

	if len(mappings) == 0 {
		ri.logger.Info("no mappings for issuer",
			"issuer", issuer.Code,
		)
		return stats, nil
	}

	ri.logger.Info("found institution mappings",
		"issuer", issuer.Code,
		"count", len(mappings),
	)

	for _, mapping := range mappings {
		ri.logger.Info("starting institution processing",
			"issuer", issuer.Code,
			"institution_id", mapping.FinancialInstitutionID,
			"slug", mapping.Config.Slug,
		)

		instStats, err := ri.processInstitution(ctx, strategy, mapping)
		if err != nil {
			ri.logger.Error("failed to process institution",
				"issuer", issuer.Code,
				"institution_id", mapping.FinancialInstitutionID,
				"error", err,
			)
			stats.Errors++
			continue
		}

		ri.logger.Info("completed institution processing",
			"issuer", issuer.Code,
			"institution_id", mapping.FinancialInstitutionID,
			"ratings_created", instStats.RatingsCreated,
			"ratings_updated", instStats.RatingsUpdated,
			"ratings_unchanged", instStats.RatingsUnchanged,
			"evidence_stored", instStats.EvidenceStored,
			"errors", instStats.Errors,
		)

		// Aggregate stats
		stats.InstitutionsProcessed++
		stats.RatingsCreated += instStats.RatingsCreated
		stats.RatingsUpdated += instStats.RatingsUpdated
		stats.RatingsUnchanged += instStats.RatingsUnchanged
		stats.EvidenceStored += instStats.EvidenceStored
	}

	return stats, nil
}

// processInstitution fetches and upserts ratings for a single institution
func (ri *RatingIngester) processInstitution(ctx context.Context, strategy IssuerStrategy, mapping domain.InstitutionIssuerMapping) (*domain.IngestionStats, error) {
	stats := &domain.IngestionStats{}

	ri.logger.Info("fetching ratings",
		"issuer", strategy.Code(),
		"institution_id", mapping.FinancialInstitutionID,
		"slug", mapping.Config.Slug,
	)

	// Fetch ratings from issuer API
	result, err := strategy.FetchRatings(ctx, mapping)
	if err != nil {
		var fetchErr domain.FetchError
		if errors.As(err, &fetchErr) && fetchErr.Evidence != nil {
			evidenceID, stored, storeErr := ri.registrar.StoreEvidence(ctx, fetchErr.Evidence)
			if storeErr != nil {
				ri.logger.Warn("failed to store evidence from fetch error",
					"error", storeErr,
				)
			}
			if stored {
				stats.EvidenceStored = 1
			}
			if evidenceID != nil {
				ri.logger.Debug("stored evidence from fetch error",
					"evidence_id", *evidenceID,
				)
			}
		}

		return nil, fmt.Errorf("failed to fetch ratings: %w", err)
	}

	instStats, err := ri.registrar.Register(ctx, mapping, result)
	if err != nil {
		return stats, err
	}

	stats.RatingsCreated += instStats.RatingsCreated
	stats.RatingsUpdated += instStats.RatingsUpdated
	stats.RatingsUnchanged += instStats.RatingsUnchanged
	stats.EvidenceStored += instStats.EvidenceStored
	stats.Errors += instStats.Errors

	return stats, nil
}
