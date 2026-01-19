package shared

import (
	"context"
	"math"
	"math/rand"
	"time"
)

// RetryPolicy defines retry behavior
type RetryPolicy struct {
	MaxRetries        int
	InitialDelay      time.Duration
	MaxDelay          time.Duration
	BackoffMultiplier float64
	Jitter            bool
}

// Retry executes a function with retry logic
func Retry(ctx context.Context, policy RetryPolicy, operation func() error) error {
	var err error
	delay := policy.InitialDelay

	for attempt := 0; attempt <= policy.MaxRetries; attempt++ {
		// Execute operation
		err = operation()
		if err == nil {
			return nil // Success
		}

		// Check if error is retryable
		if !IsRetryableError(err) {
			return err // Non-retryable error
		}

		// Check if we've exhausted retries
		if attempt >= policy.MaxRetries {
			return err // Max retries exceeded
		}

		// Calculate actual delay with jitter (use current delay, not multiplied)
		actualDelay := delay

		// Cap at max delay
		if actualDelay > policy.MaxDelay {
			actualDelay = policy.MaxDelay
		}

		// Add jitter if enabled (random variation ±25%)
		if policy.Jitter {
			jitterRange := float64(actualDelay) * 0.25
			jitterOffset := (rand.Float64() * 2 * jitterRange) - jitterRange
			actualDelay = time.Duration(float64(actualDelay) + jitterOffset)

			// Ensure delay is positive
			if actualDelay < 0 {
				actualDelay = delay
			}
		}

		// Wait with context cancellation support
		select {
		case <-time.After(actualDelay):
			// Continue to next attempt
		case <-ctx.Done():
			return ctx.Err() // Context canceled
		}

		// Update delay for next iteration (exponential backoff)
		delay = time.Duration(float64(delay) * policy.BackoffMultiplier)
	}

	return err
}

// ExponentialBackoff calculates exponential backoff delay
func ExponentialBackoff(attempt int, initialDelay, maxDelay time.Duration, multiplier float64) time.Duration {
	delay := time.Duration(float64(initialDelay) * math.Pow(multiplier, float64(attempt)))
	if delay > maxDelay {
		delay = maxDelay
	}
	return delay
}
