package shared

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"math"
	"math/rand"
	"strings"
	"time"
)

type RetryPolicy struct {
	MaxRetries        int
	InitialDelay      time.Duration
	MaxDelay          time.Duration
	BackoffMultiplier float64
	Jitter            bool
}

var DefaultRetryPolicy = RetryPolicy{
	MaxRetries:        3,
	InitialDelay:      2 * time.Second,
	MaxDelay:          30 * time.Second,
	BackoffMultiplier: 2.0,
	Jitter:            true,
}

var DBConnRetryPolicy = RetryPolicy{
	MaxRetries:        5,
	InitialDelay:      1 * time.Second,
	MaxDelay:          10 * time.Second,
	BackoffMultiplier: 2.0,
	Jitter:            true,
}

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
		logger.Info("retrying after error", "attempt", attempt+1, "max_retries", policy.MaxRetries, "delay_ms", delay.Milliseconds(), "error", lastErr)
		select {
		case <-ctx.Done():
			return fmt.Errorf("context cancelled during retry: %w", ctx.Err())
		case <-time.After(delay):
		}
	}
	return lastErr
}

func ReadAllTrimmed(reader io.Reader) (string, error) {
	bytes, err := io.ReadAll(reader)
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(string(bytes)), nil
}

func backoffDelay(attempt int, p RetryPolicy) time.Duration {
	delay := time.Duration(float64(p.InitialDelay) * math.Pow(p.BackoffMultiplier, float64(attempt)))
	if delay > p.MaxDelay {
		delay = p.MaxDelay
	}
	if p.Jitter {
		jitter := float64(delay) * 0.25
		delay = time.Duration(float64(delay) + (rand.Float64()*2*jitter - jitter))
		if delay < 0 {
			delay = p.InitialDelay
		}
	}
	return delay
}
