package cnpja

import (
	"context"
	"sync"
	"time"
)

// RateLimiter enforces rate limiting for CNPJá API calls
type RateLimiter struct {
	mu              sync.Mutex
	lastQueryTime   time.Time
	minDelaySeconds int
}

// NewRateLimiter creates a new rate limiter
func NewRateLimiter(minDelaySeconds int) *RateLimiter {
	return &RateLimiter{
		minDelaySeconds: minDelaySeconds,
		lastQueryTime:   time.Time{}, // Zero time
	}
}

// Wait blocks until enough time has passed since the last API call.
// Ensures at least minDelaySeconds seconds between API calls.
func (rl *RateLimiter) Wait(ctx context.Context) error {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	// If this is the first call, no need to wait
	if rl.lastQueryTime.IsZero() {
		rl.lastQueryTime = time.Now()
		return nil
	}

	// Calculate time elapsed since last query
	elapsed := time.Since(rl.lastQueryTime)
	minDelay := time.Duration(rl.minDelaySeconds) * time.Second

	// If not enough time has passed, wait
	if elapsed < minDelay {
		waitTime := minDelay - elapsed

		// Wait with context cancellation support
		select {
		case <-time.After(waitTime):
			// Wait complete
		case <-ctx.Done():
			return ctx.Err() // Context canceled
		}
	}

	// Update last query time
	rl.lastQueryTime = time.Now()
	return nil
}

// Reset resets the rate limiter (useful for testing)
func (rl *RateLimiter) Reset() {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	rl.lastQueryTime = time.Time{}
}
