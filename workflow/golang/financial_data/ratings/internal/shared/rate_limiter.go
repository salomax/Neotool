package shared

import (
	"context"
	"time"

	"golang.org/x/time/rate"
)

// RateLimiter wraps golang.org/x/time/rate for API throttling
type RateLimiter struct {
	limiter *rate.Limiter
}

// NewRateLimiter creates a new rate limiter with the specified interval between requests
// For example, interval of 15 seconds means 1 request per 15 seconds (rate = 1/15 = 0.0667)
func NewRateLimiter(interval time.Duration) *RateLimiter {
	// rate.Limit is requests per second
	// interval is duration between requests, so rate = 1 / interval_seconds
	rps := 1.0 / interval.Seconds()

	return &RateLimiter{
		limiter: rate.NewLimiter(rate.Limit(rps), 1), // burst of 1
	}
}

// NewRateLimiterFromRPS creates a new rate limiter with requests per second
func NewRateLimiterFromRPS(requestsPerSecond float64, burst int) *RateLimiter {
	return &RateLimiter{
		limiter: rate.NewLimiter(rate.Limit(requestsPerSecond), burst),
	}
}

// Wait blocks until the rate limit allows the next request or context is cancelled
func (rl *RateLimiter) Wait(ctx context.Context) error {
	return rl.limiter.Wait(ctx)
}

// Allow reports whether an event may happen now
func (rl *RateLimiter) Allow() bool {
	return rl.limiter.Allow()
}
