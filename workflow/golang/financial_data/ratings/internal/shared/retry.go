package shared

import (
	"context"
	"log/slog"
	"math"
	"math/rand"
	"time"
)

// RetryPolicy defines the retry behavior
type RetryPolicy struct {
	MaxRetries        int
	InitialDelay      time.Duration
	MaxDelay          time.Duration
	BackoffMultiplier float64
	Jitter            bool
}

// DefaultRetryPolicy returns a sensible default retry policy
func DefaultRetryPolicy() RetryPolicy {
	return RetryPolicy{
		MaxRetries:        3,
		InitialDelay:      1 * time.Second,
		MaxDelay:          30 * time.Second,
		BackoffMultiplier: 2.0,
		Jitter:            true,
	}
}

// Retry executes an operation with exponential backoff retry
// The isRetryable function determines if an error should be retried
func Retry[T any](
	ctx context.Context,
	policy RetryPolicy,
	operation func(ctx context.Context) (T, error),
	isRetryable func(error) bool,
	logger *slog.Logger,
) (T, error) {
	var result T
	var lastErr error

	for attempt := 0; attempt <= policy.MaxRetries; attempt++ {
		// Check context cancellation
		if ctx.Err() != nil {
			return result, ctx.Err()
		}

		// Execute operation
		result, lastErr = operation(ctx)
		if lastErr == nil {
			return result, nil
		}

		// Check if we should retry
		if !isRetryable(lastErr) {
			logger.Warn("non-retryable error",
				"error", lastErr,
				"attempt", attempt+1,
			)
			return result, lastErr
		}

		// Check if we've exhausted retries
		if attempt >= policy.MaxRetries {
			logger.Error("max retries exhausted",
				"error", lastErr,
				"attempts", attempt+1,
			)
			return result, lastErr
		}

		// Calculate delay with exponential backoff
		delay := calculateDelay(policy, attempt)

		logger.Warn("retrying after error",
			"error", lastErr,
			"attempt", attempt+1,
			"next_attempt_in", delay,
		)

		// Wait before retry
		select {
		case <-ctx.Done():
			return result, ctx.Err()
		case <-time.After(delay):
		}
	}

	return result, lastErr
}

// calculateDelay computes the delay for a given attempt using exponential backoff
func calculateDelay(policy RetryPolicy, attempt int) time.Duration {
	// Exponential backoff: initialDelay * (multiplier ^ attempt)
	delay := float64(policy.InitialDelay) * math.Pow(policy.BackoffMultiplier, float64(attempt))

	// Cap at max delay
	if delay > float64(policy.MaxDelay) {
		delay = float64(policy.MaxDelay)
	}

	// Add jitter (0-25% of delay)
	if policy.Jitter {
		jitter := delay * 0.25 * rand.Float64()
		delay += jitter
	}

	return time.Duration(delay)
}
