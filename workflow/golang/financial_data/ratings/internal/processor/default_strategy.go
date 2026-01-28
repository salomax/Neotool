package processor

import (
	"context"
	"log/slog"

	"github.com/invistus/ratings/internal/domain"
)

// DefaultStrategy uses enabled mappings and a generic issuer client.
type DefaultStrategy struct {
	issuer      domain.RatingIssuer
	client      domain.IssuerClient
	mappingRepo domain.MappingRepository
	logger      *slog.Logger
}

// NewDefaultStrategy creates a default issuer strategy.
func NewDefaultStrategy(
	issuer domain.RatingIssuer,
	client domain.IssuerClient,
	mappingRepo domain.MappingRepository,
	logger *slog.Logger,
) *DefaultStrategy {
	return &DefaultStrategy{
		issuer:      issuer,
		client:      client,
		mappingRepo: mappingRepo,
		logger:      logger.With("strategy", "default", "issuer", issuer.Code),
	}
}

func (s *DefaultStrategy) Code() string {
	return s.issuer.Code
}

func (s *DefaultStrategy) ListMappings(ctx context.Context, issuer domain.RatingIssuer) ([]domain.InstitutionIssuerMapping, int, error) {
	mappings, err := s.mappingRepo.GetEnabledByIssuer(ctx, issuer.ID)
	if err != nil {
		return nil, 0, err
	}

	return mappings, 0, nil
}

func (s *DefaultStrategy) FetchRatings(ctx context.Context, mapping domain.InstitutionIssuerMapping) (*domain.FetchResult, error) {
	return s.client.FetchRatings(ctx, mapping)
}
