package shared

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"math/rand"
	"time"
)

// RetryPolicy defines the parameters for retry behavior
type RetryPolicy struct {
	MaxRetries        int
	InitialDelay      time.Duration
	MaxDelay          time.Duration
	BackoffMultiplier float64
	Jitter            bool
}

// Retry executes the given operation with retry logic based on the policy
// It uses exponential backoff with optional jitter
func Retry(
	ctx context.Context,
	policy RetryPolicy,
	operation func(ctx context.Context) error,
	isRetryable func(error) bool,
	logger *slog.Logger,
) error {
	var lastErr error

	for attempt := 0; attempt <= policy.MaxRetries; attempt++ {
		// Execute the operation
		err := operation(ctx)
		if err == nil {
			return nil
		}

		lastErr = err

		// Check if we should retry
		if !isRetryable(err) {
			logger.Warn("error is not retryable, stopping retry attempts",
				"error", err,
				"attempt", attempt)
			return fmt.Errorf("non-retryable error: %w", err)
		}

		// Check if we've exhausted retries
		if attempt >= policy.MaxRetries {
			logger.Error("max retries exceeded",
				"attempts", attempt+1,
				"error", lastErr)
			return fmt.Errorf("max retries exceeded after %d attempts: %w", attempt+1, lastErr)
		}

		// Calculate backoff delay
		delay := calculateBackoff(attempt, policy)

		logger.Info("retrying after error",
			"attempt", attempt+1,
			"max_retries", policy.MaxRetries,
			"delay", delay,
			"error", err)

		// Wait for backoff or context cancellation
		select {
		case <-ctx.Done():
			return fmt.Errorf("context cancelled during retry: %w", ctx.Err())
		case <-time.After(delay):
			// Continue to next attempt
		}
	}

	return lastErr
}

// calculateBackoff computes the backoff delay with exponential growth and optional jitter
func calculateBackoff(attempt int, policy RetryPolicy) time.Duration {
	// Calculate exponential backoff
	backoff := float64(policy.InitialDelay) * math.Pow(policy.BackoffMultiplier, float64(attempt))
	delay := time.Duration(backoff)

	// Cap at max delay
	if delay > policy.MaxDelay {
		delay = policy.MaxDelay
	}

	// Add jitter if enabled (±25% variance)
	if policy.Jitter {
		jitterRange := float64(delay) * 0.25
		jitterOffset := (rand.Float64() * 2 * jitterRange) - jitterRange
		delay = time.Duration(float64(delay) + jitterOffset)

		// Ensure delay is not negative
		if delay < 0 {
			delay = policy.InitialDelay
		}
	}

	return delay
}
