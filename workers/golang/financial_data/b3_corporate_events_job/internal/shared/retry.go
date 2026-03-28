package shared

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"math/rand"
	"time"
)

// RetryPolicy defines parameters for retry behaviour with exponential backoff.
type RetryPolicy struct {
	MaxRetries        int
	InitialDelay      time.Duration
	MaxDelay          time.Duration
	BackoffMultiplier float64
	Jitter            bool
}

// DefaultRetryPolicy is a sensible default for external HTTP calls.
var DefaultRetryPolicy = RetryPolicy{
	MaxRetries:        3,
	InitialDelay:      1 * time.Second,
	MaxDelay:          15 * time.Second,
	BackoffMultiplier: 2.0,
	Jitter:            true,
}

// Retry executes operation with exponential-backoff retries.
func Retry(
	ctx context.Context,
	policy RetryPolicy,
	operation func(ctx context.Context) error,
	isRetryable func(error) bool,
	logger *slog.Logger,
) error {
	var lastErr error

	for attempt := 0; attempt <= policy.MaxRetries; attempt++ {
		if err := operation(ctx); err == nil {
			return nil
		} else {
			lastErr = err
		}

		if !isRetryable(lastErr) {
			return fmt.Errorf("non-retryable error: %w", lastErr)
		}
		if attempt >= policy.MaxRetries {
			return fmt.Errorf("max retries (%d) exceeded: %w", policy.MaxRetries, lastErr)
		}

		delay := backoffDelay(attempt, policy)
		logger.Info("retrying after error",
			"attempt", attempt+1,
			"max_retries", policy.MaxRetries,
			"delay", delay,
			"error", lastErr,
		)

		select {
		case <-ctx.Done():
			return fmt.Errorf("context cancelled during retry: %w", ctx.Err())
		case <-time.After(delay):
		}
	}

	return lastErr
}

func backoffDelay(attempt int, p RetryPolicy) time.Duration {
	d := time.Duration(float64(p.InitialDelay) * math.Pow(p.BackoffMultiplier, float64(attempt)))
	if d > p.MaxDelay {
		d = p.MaxDelay
	}
	if p.Jitter {
		jitter := float64(d) * 0.25
		d = time.Duration(float64(d) + (rand.Float64()*2*jitter - jitter))
		if d < 0 {
			d = p.InitialDelay
		}
	}
	return d
}
