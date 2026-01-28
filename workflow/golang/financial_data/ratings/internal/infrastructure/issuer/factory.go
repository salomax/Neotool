package issuer

import (
	"fmt"
	"log/slog"
	"time"

	"github.com/invistus/ratings/internal/domain"
	"github.com/invistus/ratings/internal/infrastructure/issuer/fitch"
	"github.com/invistus/ratings/internal/shared"
)

// Factory creates issuer-specific clients
type Factory struct {
	timeout     time.Duration
	throttle    time.Duration
	retryPolicy shared.RetryPolicy
	logger      *slog.Logger
}

// NewFactory creates a new issuer client factory
func NewFactory(timeout, throttle time.Duration, retryPolicy shared.RetryPolicy, logger *slog.Logger) *Factory {
	return &Factory{
		timeout:     timeout,
		throttle:    throttle,
		retryPolicy: retryPolicy,
		logger:      logger,
	}
}

// CreateClient creates a client for the given issuer
func (f *Factory) CreateClient(issuer domain.RatingIssuer) (domain.IssuerClient, error) {
	switch issuer.Code {
	case fitch.IssuerCode:
		// Use issuer-specific throttle if configured, otherwise use default
		throttle := f.throttle
		if issuer.Config.ThrottleMs > 0 {
			throttle = time.Duration(issuer.Config.ThrottleMs) * time.Millisecond
		}

		return fitch.NewClient(issuer, f.timeout, throttle, f.retryPolicy, f.logger), nil

	default:
		return nil, fmt.Errorf("unsupported issuer: %s", issuer.Code)
	}
}

// SupportsIssuer checks if the factory supports the given issuer code
func (f *Factory) SupportsIssuer(code string) bool {
	switch code {
	case fitch.IssuerCode:
		return true
	default:
		return false
	}
}
