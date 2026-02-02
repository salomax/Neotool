package shared

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRetry_Success(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        3,
		InitialDelay:      10 * time.Millisecond,
		MaxDelay:          100 * time.Millisecond,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	attempts := 0
	operation := func() error {
		attempts++
		return nil
	}

	err := Retry(context.Background(), policy, operation)

	assert.NoError(t, err)
	assert.Equal(t, 1, attempts, "should succeed on first attempt")
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
	operation := func() error {
		attempts++
		if attempts < 3 {
			return errors.New("HTTP 500: server error") // Retryable
		}
		return nil
	}

	err := Retry(context.Background(), policy, operation)

	assert.NoError(t, err)
	assert.Equal(t, 3, attempts, "should succeed on third attempt")
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
	retryableErr := errors.New("HTTP 500: server error")
	operation := func() error {
		attempts++
		return retryableErr
	}

	err := Retry(context.Background(), policy, operation)

	assert.Error(t, err)
	assert.Equal(t, retryableErr, err)
	assert.Equal(t, 3, attempts, "should attempt max_retries + 1 times (0, 1, 2)")
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
	nonRetryableErr := errors.New("HTTP 404: not found")
	operation := func() error {
		attempts++
		return nonRetryableErr
	}

	err := Retry(context.Background(), policy, operation)

	assert.Error(t, err)
	assert.Equal(t, nonRetryableErr, err)
	assert.Equal(t, 1, attempts, "should not retry non-retryable errors")
}

func TestRetry_ContextCanceled(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        3,
		InitialDelay:      100 * time.Millisecond,
		MaxDelay:          1 * time.Second,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	ctx, cancel := context.WithCancel(context.Background())

	attempts := 0
	operation := func() error {
		attempts++
		if attempts == 2 {
			cancel() // Cancel context after second attempt
		}
		return errors.New("HTTP 500: server error") // Retryable
	}

	err := Retry(ctx, policy, operation)

	assert.Error(t, err)
	assert.Equal(t, context.Canceled, err)
	assert.Equal(t, 2, attempts, "should stop when context is canceled")
}

func TestRetry_BackoffProgression(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        3,
		InitialDelay:      50 * time.Millisecond,
		MaxDelay:          500 * time.Millisecond,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	attempts := 0
	attemptTimes := make([]time.Time, 0)

	operation := func() error {
		attempts++
		attemptTimes = append(attemptTimes, time.Now())
		return errors.New("HTTP 500: server error") // Retryable
	}

	_ = Retry(context.Background(), policy, operation)

	require.Equal(t, 4, attempts, "should have 4 attempts (0, 1, 2, 3)")
	require.Len(t, attemptTimes, 4)

	// Check backoff delays between attempts
	// Attempt 0 -> Attempt 1: ~50ms delay
	delay1 := attemptTimes[1].Sub(attemptTimes[0])
	assert.InDelta(t, 50*time.Millisecond, delay1, float64(20*time.Millisecond), "first delay should be ~50ms")

	// Attempt 1 -> Attempt 2: ~100ms delay (50 * 2)
	delay2 := attemptTimes[2].Sub(attemptTimes[1])
	assert.InDelta(t, 100*time.Millisecond, delay2, float64(20*time.Millisecond), "second delay should be ~100ms")

	// Attempt 2 -> Attempt 3: ~200ms delay (100 * 2)
	delay3 := attemptTimes[3].Sub(attemptTimes[2])
	assert.InDelta(t, 200*time.Millisecond, delay3, float64(20*time.Millisecond), "third delay should be ~200ms")
}

func TestRetry_MaxDelayEnforced(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        5,
		InitialDelay:      100 * time.Millisecond,
		MaxDelay:          150 * time.Millisecond, // Cap delays at 150ms
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	attempts := 0
	attemptTimes := make([]time.Time, 0)

	operation := func() error {
		attempts++
		attemptTimes = append(attemptTimes, time.Now())
		return errors.New("HTTP 500: server error") // Retryable
	}

	_ = Retry(context.Background(), policy, operation)

	require.GreaterOrEqual(t, len(attemptTimes), 4)

	// After first delay (100ms), next would be 200ms but should be capped at 150ms
	delay2 := attemptTimes[2].Sub(attemptTimes[1])
	assert.LessOrEqual(t, delay2, 170*time.Millisecond, "delay should be capped at maxDelay")
}

func TestRetry_WithJitter(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        2,
		InitialDelay:      100 * time.Millisecond,
		MaxDelay:          500 * time.Millisecond,
		BackoffMultiplier: 2.0,
		Jitter:            true, // Enable jitter
	}

	attempts := 0
	attemptTimes := make([]time.Time, 0)

	operation := func() error {
		attempts++
		attemptTimes = append(attemptTimes, time.Now())
		return errors.New("HTTP 500: server error") // Retryable
	}

	_ = Retry(context.Background(), policy, operation)

	require.Equal(t, 3, attempts)
	require.Len(t, attemptTimes, 3)

	// With jitter, delays should vary but stay within bounds (±25%)
	delay1 := attemptTimes[1].Sub(attemptTimes[0])
	// Expected: 100ms ± 25% = 75ms to 125ms
	assert.GreaterOrEqual(t, delay1, 70*time.Millisecond, "delay with jitter should be >= 75% of base")
	assert.LessOrEqual(t, delay1, 130*time.Millisecond, "delay with jitter should be <= 125% of base")
}

func TestRetry_ContextDeadlineExceeded(t *testing.T) {
	policy := RetryPolicy{
		MaxRetries:        5,
		InitialDelay:      50 * time.Millisecond,
		MaxDelay:          500 * time.Millisecond,
		BackoffMultiplier: 2.0,
		Jitter:            false,
	}

	// Context with very short timeout
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()

	attempts := 0
	operation := func() error {
		attempts++
		return errors.New("HTTP 500: server error") // Retryable
	}

	err := Retry(ctx, policy, operation)

	assert.Error(t, err)
	assert.Equal(t, context.DeadlineExceeded, err)
	assert.Less(t, attempts, 5, "should stop before max retries due to context timeout")
}

func TestExponentialBackoff(t *testing.T) {
	tests := []struct {
		name           string
		attempt        int
		initialDelay   time.Duration
		maxDelay       time.Duration
		multiplier     float64
		expectedDelay  time.Duration
	}{
		{
			name:          "attempt 0",
			attempt:       0,
			initialDelay:  100 * time.Millisecond,
			maxDelay:      10 * time.Second,
			multiplier:    2.0,
			expectedDelay: 100 * time.Millisecond,
		},
		{
			name:          "attempt 1",
			attempt:       1,
			initialDelay:  100 * time.Millisecond,
			maxDelay:      10 * time.Second,
			multiplier:    2.0,
			expectedDelay: 200 * time.Millisecond,
		},
		{
			name:          "attempt 2",
			attempt:       2,
			initialDelay:  100 * time.Millisecond,
			maxDelay:      10 * time.Second,
			multiplier:    2.0,
			expectedDelay: 400 * time.Millisecond,
		},
		{
			name:          "capped at max delay",
			attempt:       10,
			initialDelay:  100 * time.Millisecond,
			maxDelay:      1 * time.Second,
			multiplier:    2.0,
			expectedDelay: 1 * time.Second,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			delay := ExponentialBackoff(tt.attempt, tt.initialDelay, tt.maxDelay, tt.multiplier)
			assert.Equal(t, tt.expectedDelay, delay)
		})
	}
}
