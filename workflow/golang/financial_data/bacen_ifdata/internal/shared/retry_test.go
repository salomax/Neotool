package shared

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"testing"
	"time"
)

func TestCalculateBackoff(t *testing.T) {
	tests := []struct {
		name        string
		attempt     int
		policy      RetryPolicy
		expectDelay time.Duration
	}{
		{
			name:    "first attempt",
			attempt: 0,
			policy: RetryPolicy{
				InitialDelay:      100 * time.Millisecond,
				MaxDelay:          1 * time.Second,
				BackoffMultiplier: 2.0,
				Jitter:            false,
			},
			expectDelay: 100 * time.Millisecond,
		},
		{
			name:    "second attempt",
			attempt: 1,
			policy: RetryPolicy{
				InitialDelay:      100 * time.Millisecond,
				MaxDelay:          1 * time.Second,
				BackoffMultiplier: 2.0,
				Jitter:            false,
			},
			expectDelay: 200 * time.Millisecond,
		},
		{
			name:    "third attempt",
			attempt: 2,
			policy: RetryPolicy{
				InitialDelay:      100 * time.Millisecond,
				MaxDelay:          1 * time.Second,
				BackoffMultiplier: 2.0,
				Jitter:            false,
			},
			expectDelay: 400 * time.Millisecond,
		},
		{
			name:    "capped at max delay",
			attempt: 10,
			policy: RetryPolicy{
				InitialDelay:      100 * time.Millisecond,
				MaxDelay:          1 * time.Second,
				BackoffMultiplier: 2.0,
				Jitter:            false,
			},
			expectDelay: 1 * time.Second,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			delay := calculateBackoff(tt.attempt, tt.policy)
			if delay != tt.expectDelay {
				t.Errorf("calculateBackoff() = %v, want %v", delay, tt.expectDelay)
			}
		})
	}
}

func TestCalculateBackoff_WithJitter(t *testing.T) {
	policy := RetryPolicy{
		InitialDelay:      100 * time.Millisecond,
		MaxDelay:          1 * time.Second,
		BackoffMultiplier: 2.0,
		Jitter:            true,
	}

	// Run multiple times to ensure jitter varies
	delays := make(map[time.Duration]bool)
	for i := 0; i < 20; i++ {
		delay := calculateBackoff(1, policy)
		delays[delay] = true

		// Jitter should be ±25% of 200ms = [150ms, 250ms]
		if delay < 150*time.Millisecond || delay > 250*time.Millisecond {
			t.Errorf("delay %v out of expected range [150ms, 250ms]", delay)
		}
	}

	// Should have some variation
	if len(delays) < 5 {
		t.Errorf("jitter not varying enough, only %d unique delays", len(delays))
	}
}

func TestRetry_Success(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        3,
		InitialDelay:      10 * time.Millisecond,
		MaxDelay:          100 * time.Millisecond,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	attempts := 0
	operation := func(ctx context.Context) error {
		attempts++
		return nil // Success on first attempt
	}

	isRetryable := func(err error) bool {
		return true
	}

	logger := newTestLogger()
	err := Retry(context.Background(), policy, operation, isRetryable, logger)

	if err != nil {
		t.Errorf("Retry() error = %v, want nil", err)
	}
	if attempts != 1 {
		t.Errorf("attempts = %d, want 1", attempts)
	}
}

func TestRetry_SuccessAfterRetries(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        3,
		InitialDelay:      10 * time.Millisecond,
		MaxDelay:          100 * time.Millisecond,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	attempts := 0
	operation := func(ctx context.Context) error {
		attempts++
		if attempts < 3 {
			return errors.New("temporary error")
		}
		return nil // Success on third attempt
	}

	isRetryable := func(err error) bool {
		return true
	}

	logger := newTestLogger()
	err := Retry(context.Background(), policy, operation, isRetryable, logger)

	if err != nil {
		t.Errorf("Retry() error = %v, want nil", err)
	}
	if attempts != 3 {
		t.Errorf("attempts = %d, want 3", attempts)
	}
}

func TestRetry_MaxRetriesExceeded(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        2,
		InitialDelay:      10 * time.Millisecond,
		MaxDelay:          100 * time.Millisecond,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	attempts := 0
	operation := func(ctx context.Context) error {
		attempts++
		return errors.New("persistent error")
	}

	isRetryable := func(err error) bool {
		return true
	}

	logger := newTestLogger()
	err := Retry(context.Background(), policy, operation, isRetryable, logger)

	if err == nil {
		t.Error("Retry() error = nil, want error")
	}
	// MaxRetries=2 means: initial attempt + 2 retries = 3 total attempts
	if attempts != 3 {
		t.Errorf("attempts = %d, want 3", attempts)
	}
}

func TestRetry_NonRetryableError(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        3,
		InitialDelay:      10 * time.Millisecond,
		MaxDelay:          100 * time.Millisecond,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	attempts := 0
	operation := func(ctx context.Context) error {
		attempts++
		return errors.New("non-retryable error")
	}

	isRetryable := func(err error) bool {
		return false // Never retry
	}

	logger := newTestLogger()
	err := Retry(context.Background(), policy, operation, isRetryable, logger)

	if err == nil {
		t.Error("Retry() error = nil, want error")
	}
	if attempts != 1 {
		t.Errorf("attempts = %d, want 1 (should not retry)", attempts)
	}
}

func TestRetry_ContextCanceled(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        3,
		InitialDelay:      100 * time.Millisecond,
		MaxDelay:          1 * time.Second,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	attempts := 0
	operation := func(ctx context.Context) error {
		attempts++
		return errors.New("error requiring retry")
	}

	isRetryable := func(err error) bool {
		return true
	}

	// Cancel context after first attempt
	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		time.Sleep(50 * time.Millisecond)
		cancel()
	}()

	logger := newTestLogger()
	err := Retry(ctx, policy, operation, isRetryable, logger)

	if err == nil {
		t.Error("Retry() error = nil, want context canceled error")
	}
	if !errors.Is(err, context.Canceled) {
		t.Errorf("Retry() error = %v, want context.Canceled", err)
	}
	// Should attempt once, then cancel during backoff
	if attempts > 2 {
		t.Errorf("attempts = %d, want ≤ 2", attempts)
	}
}

// newTestLogger creates a no-op logger for tests
func newTestLogger() *slog.Logger {
	// Create a logger that discards all output
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}
