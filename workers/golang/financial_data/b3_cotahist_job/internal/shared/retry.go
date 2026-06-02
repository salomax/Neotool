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
	InitialDelay:      2 * time.Second,
	MaxDelay:          30 * time.Second,
	BackoffMultiplier: 2.0,
	Jitter:            true,
}

// B3DownloadRetryPolicy is tuned for B3's CDN (Cloudflare), which returns
// transient 520 errors under load. Annual files are 30–150 MB, so each
// transfer takes tens of seconds — giving the CDN more time to recover
// between retries is more effective than rapid short-interval retries.
var B3DownloadRetryPolicy = RetryPolicy{
	MaxRetries:        6,
	InitialDelay:      10 * time.Second,
	MaxDelay:          120 * time.Second,
	BackoffMultiplier: 2.0,
	Jitter:            true,
}

// DBConnRetryPolicy is used when establishing the initial DB connection pool.
var DBConnRetryPolicy = RetryPolicy{
	MaxRetries:        5,
	InitialDelay:      1 * time.Second,
	MaxDelay:          10 * time.Second,
	BackoffMultiplier: 2.0,
	Jitter:            true,
}

// Retry executes operation with exponential-backoff retries.
// isRetryable determines whether a given error warrants a retry.
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
			"delay_ms", delay.Milliseconds(),
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
