package processor

import (
	"fmt"
	"log/slog"

	"github.com/invistus/ratings/internal/domain"
	"github.com/invistus/ratings/internal/infrastructure/issuer/fitch"
)

// StrategyFactory creates issuer strategies based on issuer code.
type StrategyFactory struct {
	mappingRepo   domain.MappingRepository
	clientFactory domain.IssuerClientFactory
	logger        *slog.Logger
}

// NewStrategyFactory creates a new strategy factory.
func NewStrategyFactory(
	mappingRepo domain.MappingRepository,
	clientFactory domain.IssuerClientFactory,
	logger *slog.Logger,
) *StrategyFactory {
	return &StrategyFactory{
		mappingRepo:   mappingRepo,
		clientFactory: clientFactory,
		logger:        logger,
	}
}

// CreateStrategy creates an issuer strategy for the given issuer.
func (f *StrategyFactory) CreateStrategy(issuer domain.RatingIssuer) (IssuerStrategy, error) {
	if !f.clientFactory.SupportsIssuer(issuer.Code) {
		return nil, fmt.Errorf("unsupported issuer: %s", issuer.Code)
	}

	client, err := f.clientFactory.CreateClient(issuer)
	if err != nil {
		return nil, err
	}

	switch issuer.Code {
	case fitch.IssuerCode:
		fitchClient, ok := client.(fitchClient)
		if !ok {
			return nil, fmt.Errorf("fitch client does not support mapping resolution")
		}
		return NewFitchStrategy(issuer, fitchClient, f.mappingRepo, f.logger), nil
	default:
		return NewDefaultStrategy(issuer, client, f.mappingRepo, f.logger), nil
	}
}

// SupportsIssuer checks if issuer code is supported.
func (f *StrategyFactory) SupportsIssuer(code string) bool {
	return f.clientFactory.SupportsIssuer(code)
}
