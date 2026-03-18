package shared

import (
	"context"
	"testing"
	"time"
)

func TestNewRateLimiter(t *testing.T) {
	rl := NewRateLimiter(10.0, 5)
	if rl == nil {
		t.Fatal("NewRateLimiter() returned nil")
	}
	if rl.limiter == nil {
		t.Error("limiter is nil")
	}
}

func TestRateLimiter_Wait(t *testing.T) {
	// Allow 10 requests per second
	rl := NewRateLimiter(10.0, 1)
	ctx := context.Background()

	// First request should succeed immediately
	start := time.Now()
	err := rl.Wait(ctx)
	elapsed := time.Since(start)

	if err != nil {
		t.Errorf("Wait() error = %v, want nil", err)
	}
	if elapsed > 50*time.Millisecond {
		t.Errorf("First request took %v, should be nearly instant", elapsed)
	}
}

func TestRateLimiter_WaitRateLimits(t *testing.T) {
	// Allow 2 requests per second (500ms between requests)
	rl := NewRateLimiter(2.0, 1)
	ctx := context.Background()

	// First request - immediate
	start := time.Now()
	err := rl.Wait(ctx)
	if err != nil {
		t.Fatalf("First Wait() error = %v", err)
	}

	// Second request - should wait ~500ms
	err = rl.Wait(ctx)
	elapsed := time.Since(start)

	if err != nil {
		t.Errorf("Second Wait() error = %v, want nil", err)
	}

	// Should take at least 450ms (with some margin)
	if elapsed < 450*time.Millisecond {
		t.Errorf("Second request took %v, expected at least 450ms", elapsed)
	}
}

func TestRateLimiter_Burst(t *testing.T) {
	// Allow 1 request per second, but burst of 3
	rl := NewRateLimiter(1.0, 3)
	ctx := context.Background()

	start := time.Now()

	// First 3 requests should succeed quickly (burst)
	for i := 0; i < 3; i++ {
		err := rl.Wait(ctx)
		if err != nil {
			t.Fatalf("Request %d error = %v", i+1, err)
		}
	}

	elapsed := time.Since(start)
	if elapsed > 100*time.Millisecond {
		t.Errorf("Burst of 3 requests took %v, should be nearly instant", elapsed)
	}

	// 4th request should wait ~1 second
	start = time.Now()
	err := rl.Wait(ctx)
	elapsed = time.Since(start)

	if err != nil {
		t.Errorf("4th request error = %v", err)
	}
	if elapsed < 900*time.Millisecond {
		t.Errorf("4th request took %v, expected at least 900ms", elapsed)
	}
}

func TestRateLimiter_ContextCanceled(t *testing.T) {
	// Very slow rate (1 per 10 seconds)
	rl := NewRateLimiter(0.1, 1)

	// First request to consume the token
	rl.Wait(context.Background())

	// Cancel context before waiting
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	err := rl.Wait(ctx)
	if err == nil {
		t.Error("Wait() with canceled context should return error")
	}
	if err != context.Canceled {
		t.Errorf("Wait() error = %v, want context.Canceled", err)
	}
}

func TestRateLimiter_ContextTimeout(t *testing.T) {
	// Very slow rate (1 per 10 seconds)
	rl := NewRateLimiter(0.1, 1)

	// First request to consume the token
	rl.Wait(context.Background())

	// Context with very short timeout
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	start := time.Now()
	err := rl.Wait(ctx)
	elapsed := time.Since(start)

	if err == nil {
		t.Error("Wait() should return error when context times out")
	}
	// Should timeout quickly
	if elapsed > 200*time.Millisecond {
		t.Errorf("Timeout took %v, should be around 50ms", elapsed)
	}
}

func TestRateLimiter_Allow(t *testing.T) {
	rl := NewRateLimiter(10.0, 2)

	// First request should be allowed
	if !rl.Allow() {
		t.Error("First Allow() = false, want true")
	}

	// Second request should be allowed (burst)
	if !rl.Allow() {
		t.Error("Second Allow() = false, want true")
	}

	// Third request should be denied (no tokens left)
	if rl.Allow() {
		t.Error("Third Allow() = true, want false")
	}

	// Wait for token to refill (100ms at 10 req/s)
	time.Sleep(150 * time.Millisecond)

	// Should be allowed again
	if !rl.Allow() {
		t.Error("Allow() after refill = false, want true")
	}
}

func TestRateLimiter_SetLimit(t *testing.T) {
	rl := NewRateLimiter(1.0, 1)

	// Consume the token
	rl.Allow()

	// At 1 req/s, next token takes 1 second
	time.Sleep(100 * time.Millisecond)
	if rl.Allow() {
		t.Error("Allow() after 100ms = true, want false (rate is 1/s)")
	}

	// Increase rate to 10 req/s
	rl.SetLimit(10.0)

	// Should have a token now (100ms is enough at 10 req/s)
	time.Sleep(100 * time.Millisecond)
	if !rl.Allow() {
		t.Error("Allow() after increasing rate = false, want true")
	}
}

func TestRateLimiter_ConcurrentAccess(t *testing.T) {
	rl := NewRateLimiter(100.0, 10)
	ctx := context.Background()

	// Run multiple goroutines concurrently
	errChan := make(chan error, 20)
	for i := 0; i < 20; i++ {
		go func() {
			errChan <- rl.Wait(ctx)
		}()
	}

	// Collect results
	for i := 0; i < 20; i++ {
		if err := <-errChan; err != nil {
			t.Errorf("Concurrent Wait() error = %v", err)
		}
	}
}
