package processor

import (
	"context"
	"log/slog"
	"strings"

	"github.com/google/uuid"
	"github.com/invistus/ratings/internal/domain"
)

type fitchClient interface {
	domain.IssuerClient
	SuggestSlug(ctx context.Context, term string) (string, error)
	FetchEntityName(ctx context.Context, slug string) (string, error)
}

// FitchStrategy resolves mappings via Fitch suggest/entity APIs.
type FitchStrategy struct {
	issuer      domain.RatingIssuer
	client      fitchClient
	mappingRepo domain.MappingRepository
	logger      *slog.Logger
}

// NewFitchStrategy creates a Fitch strategy.
func NewFitchStrategy(
	issuer domain.RatingIssuer,
	client fitchClient,
	mappingRepo domain.MappingRepository,
	logger *slog.Logger,
) *FitchStrategy {
	return &FitchStrategy{
		issuer:      issuer,
		client:      client,
		mappingRepo: mappingRepo,
		logger:      logger.With("strategy", "fitch", "issuer", issuer.Code),
	}
}

func (s *FitchStrategy) Code() string {
	return s.issuer.Code
}

func (s *FitchStrategy) ListMappings(ctx context.Context, issuer domain.RatingIssuer) ([]domain.InstitutionIssuerMapping, int, error) {
	entries, err := s.mappingRepo.ListInstitutionsWithIssuerMapping(ctx, issuer.ID)
	if err != nil {
		return nil, 0, err
	}

	var mappings []domain.InstitutionIssuerMapping
	var mappingErrors int

	for _, entry := range entries {
		s.logger.Info("evaluating institution",
			"institution_id", entry.Institution.ID,
			"legal_name", entry.Institution.LegalName,
		)

		if entry.Mapping != nil {
			if !entry.Mapping.Enabled {
				s.logger.Info("skipping disabled mapping",
					"institution_id", entry.Mapping.FinancialInstitutionID,
				)
				continue
			}
			if entry.Mapping.Config.Slug == "" {
				s.logger.Warn("enabled mapping missing slug",
					"institution_id", entry.Mapping.FinancialInstitutionID,
				)
				mappingErrors++
				continue
			}
			mappings = append(mappings, *entry.Mapping)
			continue
		}

		s.logger.Info("resolving mapping",
			"institution_id", entry.Institution.ID,
			"legal_name", entry.Institution.LegalName,
		)

		slug, err := s.client.SuggestSlug(ctx, entry.Institution.LegalName)
		if err != nil {
			s.logger.Error("failed to suggest slug",
				"institution_id", entry.Institution.ID,
				"legal_name", entry.Institution.LegalName,
				"error", err,
			)
			mappingErrors++
			continue
		}

		if slug == "" {
			s.logger.Info("no slug suggested; disabling mapping",
				"institution_id", entry.Institution.ID,
				"legal_name", entry.Institution.LegalName,
			)
			if err := s.upsertMapping(ctx, domain.InstitutionIssuerMapping{
				ID:                     uuid.New(),
				RatingIssuerID:         issuer.ID,
				FinancialInstitutionID: entry.Institution.ID,
				Enabled:                false,
			}); err != nil {
				s.logger.Error("failed to store disabled mapping",
					"institution_id", entry.Institution.ID,
					"error", err,
				)
				mappingErrors++
			}
			continue
		}

		entityName, err := s.client.FetchEntityName(ctx, slug)
		if err != nil {
			s.logger.Error("failed to fetch entity name",
				"institution_id", entry.Institution.ID,
				"slug", slug,
				"error", err,
			)
			mappingErrors++
			continue
		}

		if !legalNameMatches(entityName, entry.Institution.LegalName) {
			s.logger.Info("entity name mismatch; disabling mapping",
				"institution_id", entry.Institution.ID,
				"legal_name", entry.Institution.LegalName,
				"entity_name", entityName,
				"slug", slug,
			)
			if err := s.upsertMapping(ctx, domain.InstitutionIssuerMapping{
				ID:                     uuid.New(),
				RatingIssuerID:         issuer.ID,
				FinancialInstitutionID: entry.Institution.ID,
				Enabled:                false,
			}); err != nil {
				s.logger.Error("failed to store invalid mapping",
					"institution_id", entry.Institution.ID,
					"slug", slug,
					"error", err,
				)
				mappingErrors++
			}
			continue
		}

		config := domain.MappingConfig{Slug: slug}
		newMapping := domain.InstitutionIssuerMapping{
			ID:                     uuid.New(),
			RatingIssuerID:         issuer.ID,
			FinancialInstitutionID: entry.Institution.ID,
			Config:                 config,
			Enabled:                true,
		}
		s.logger.Info("enabling mapping",
			"institution_id", entry.Institution.ID,
			"slug", slug,
		)
		if err := s.upsertMapping(ctx, newMapping); err != nil {
			s.logger.Error("failed to store mapping",
				"institution_id", entry.Institution.ID,
				"slug", slug,
				"error", err,
			)
			mappingErrors++
			continue
		}

		mappings = append(mappings, newMapping)
	}

	return mappings, mappingErrors, nil
}

func (s *FitchStrategy) FetchRatings(ctx context.Context, mapping domain.InstitutionIssuerMapping) (*domain.FetchResult, error) {
	return s.client.FetchRatings(ctx, mapping)
}

func (s *FitchStrategy) upsertMapping(ctx context.Context, mapping domain.InstitutionIssuerMapping) error {
	return s.mappingRepo.Upsert(ctx, &mapping)
}

func legalNameMatches(entityName string, legalName string) bool {
	return normalizeLegalName(entityName) == normalizeLegalName(legalName)
}

func normalizeLegalName(name string) string {
	replacer := strings.NewReplacer(".", " ", "/", " ", "-", " ")
	cleaned := replacer.Replace(name)
	fields := strings.Fields(cleaned)
	if len(fields) == 0 {
		return ""
	}
	return strings.ToUpper(strings.Join(fields, " "))
}
