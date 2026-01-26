package processor

import (
	"context"

	"github.com/invistus/ratings/internal/domain"
)

// IssuerStrategy defines issuer-specific mapping and fetching behavior.
type IssuerStrategy interface {
	Code() string
	ListMappings(ctx context.Context, issuer domain.RatingIssuer) ([]domain.InstitutionIssuerMapping, int, error)
	FetchRatings(ctx context.Context, mapping domain.InstitutionIssuerMapping) (*domain.FetchResult, error)
}

// IssuerStrategyFactory builds issuer strategies.
type IssuerStrategyFactory interface {
	CreateStrategy(issuer domain.RatingIssuer) (IssuerStrategy, error)
	SupportsIssuer(code string) bool
}
