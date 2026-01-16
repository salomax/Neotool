package shared

import (
	"context"
	"golang.org/x/time/rate"
)

// RateLimiter controls the rate of operations using token bucket algorithm
type RateLimiter struct {
	limiter *rate.Limiter
}

// NewRateLimiter creates a new rate limiter
// requestsPerSecond: maximum requests allowed per second
// burst: maximum burst size (allows temporary spikes)
func NewRateLimiter(requestsPerSecond float64, burst int) *RateLimiter {
	return &RateLimiter{
		limiter: rate.NewLimiter(rate.Limit(requestsPerSecond), burst),
	}
}

// Wait blocks until a token is available or context is cancelled
func (rl *RateLimiter) Wait(ctx context.Context) error {
	return rl.limiter.Wait(ctx)
}

// Allow checks if a request can proceed without blocking
func (rl *RateLimiter) Allow() bool {
	return rl.limiter.Allow()
}

// SetLimit updates the rate limit dynamically
func (rl *RateLimiter) SetLimit(requestsPerSecond float64) {
	rl.limiter.SetLimit(rate.Limit(requestsPerSecond))
}
