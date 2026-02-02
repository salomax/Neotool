package shared

import (
	"errors"
	"testing"
	"time"
)

func TestCircuitBreaker_ClosedState(t *testing.T) {
	cb := NewCircuitBreaker(CircuitBreakerConfig{
		MaxFailures:         3,
		Timeout:             100 * time.Millisecond,
		MaxHalfOpenRequests: 2,
	})

	// Should start in closed state
	if cb.GetState() != StateClosed {
		t.Errorf("initial state = %v, want CLOSED", cb.GetState())
	}

	// Successful operations should keep circuit closed
	for i := 0; i < 10; i++ {
		err := cb.Execute(func() error {
			return nil
		})
		if err != nil {
			t.Errorf("Execute() error = %v, want nil", err)
		}
		if cb.GetState() != StateClosed {
			t.Errorf("state after success = %v, want CLOSED", cb.GetState())
		}
	}
}

func TestCircuitBreaker_OpensAfterFailures(t *testing.T) {
	cb := NewCircuitBreaker(CircuitBreakerConfig{
		MaxFailures:         3,
		Timeout:             100 * time.Millisecond,
		MaxHalfOpenRequests: 2,
	})

	testErr := errors.New("test error")

	// First 2 failures - should stay closed
	for i := 0; i < 2; i++ {
		err := cb.Execute(func() error {
			return testErr
		})
		if err != testErr {
			t.Errorf("Execute() error = %v, want %v", err, testErr)
		}
		if cb.GetState() != StateClosed {
			t.Errorf("state after %d failures = %v, want CLOSED", i+1, cb.GetState())
		}
	}

	// 3rd failure - should open
	err := cb.Execute(func() error {
		return testErr
	})
	if err != testErr {
		t.Errorf("Execute() error = %v, want %v", err, testErr)
	}
	if cb.GetState() != StateOpen {
		t.Errorf("state after 3 failures = %v, want OPEN", cb.GetState())
	}

	// Subsequent calls should fail fast
	err = cb.Execute(func() error {
		t.Error("operation should not be called when circuit is open")
		return nil
	})
	if !errors.Is(err, ErrCircuitOpen) {
		t.Errorf("Execute() error = %v, want ErrCircuitOpen", err)
	}
}

func TestCircuitBreaker_HalfOpenTransition(t *testing.T) {
	cb := NewCircuitBreaker(CircuitBreakerConfig{
		MaxFailures:         2,
		Timeout:             50 * time.Millisecond,
		MaxHalfOpenRequests: 2,
	})

	testErr := errors.New("test error")

	// Open the circuit
	for i := 0; i < 2; i++ {
		cb.Execute(func() error { return testErr })
	}

	if cb.GetState() != StateOpen {
		t.Fatalf("state = %v, want OPEN", cb.GetState())
	}

	// Wait for timeout
	time.Sleep(60 * time.Millisecond)

	// Next request should transition to half-open
	called := false
	err := cb.Execute(func() error {
		called = true
		return nil
	})

	if err != nil {
		t.Errorf("Execute() error = %v, want nil", err)
	}
	if !called {
		t.Error("operation should be called in half-open state")
	}
	if cb.GetState() != StateHalfOpen {
		t.Errorf("state = %v, want HALF_OPEN", cb.GetState())
	}
}

func TestCircuitBreaker_HalfOpenToClosedOnSuccess(t *testing.T) {
	cb := NewCircuitBreaker(CircuitBreakerConfig{
		MaxFailures:         2,
		Timeout:             50 * time.Millisecond,
		MaxHalfOpenRequests: 2,
	})

	// Open the circuit
	for i := 0; i < 2; i++ {
		cb.Execute(func() error { return errors.New("error") })
	}

	// Wait for timeout and transition to half-open
	time.Sleep(60 * time.Millisecond)

	// First success in half-open
	cb.Execute(func() error { return nil })
	if cb.GetState() != StateHalfOpen {
		t.Errorf("state after 1 success = %v, want HALF_OPEN", cb.GetState())
	}

	// Second success - should close circuit
	cb.Execute(func() error { return nil })
	if cb.GetState() != StateClosed {
		t.Errorf("state after 2 successes = %v, want CLOSED", cb.GetState())
	}

	// Circuit should stay closed on further successes
	cb.Execute(func() error { return nil })
	if cb.GetState() != StateClosed {
		t.Errorf("state = %v, want CLOSED", cb.GetState())
	}
}

func TestCircuitBreaker_HalfOpenToOpenOnFailure(t *testing.T) {
	cb := NewCircuitBreaker(CircuitBreakerConfig{
		MaxFailures:         2,
		Timeout:             50 * time.Millisecond,
		MaxHalfOpenRequests: 2,
	})

	// Open the circuit
	for i := 0; i < 2; i++ {
		cb.Execute(func() error { return errors.New("error") })
	}

	// Wait for timeout and transition to half-open
	time.Sleep(60 * time.Millisecond)
	cb.Execute(func() error { return nil }) // First test request

	if cb.GetState() != StateHalfOpen {
		t.Fatalf("state = %v, want HALF_OPEN", cb.GetState())
	}

	// Failure in half-open - should reopen
	cb.Execute(func() error { return errors.New("error") })
	if cb.GetState() != StateOpen {
		t.Errorf("state after failure = %v, want OPEN", cb.GetState())
	}
}

func TestCircuitBreaker_Reset(t *testing.T) {
	cb := NewCircuitBreaker(CircuitBreakerConfig{
		MaxFailures:         2,
		Timeout:             100 * time.Millisecond,
		MaxHalfOpenRequests: 2,
	})

	// Open the circuit
	for i := 0; i < 2; i++ {
		cb.Execute(func() error { return errors.New("error") })
	}

	if cb.GetState() != StateOpen {
		t.Fatalf("state = %v, want OPEN", cb.GetState())
	}

	// Reset should close the circuit
	cb.Reset()
	if cb.GetState() != StateClosed {
		t.Errorf("state after reset = %v, want CLOSED", cb.GetState())
	}
	if cb.GetConsecutiveFailures() != 0 {
		t.Errorf("consecutive failures = %d, want 0", cb.GetConsecutiveFailures())
	}

	// Should work normally after reset
	err := cb.Execute(func() error { return nil })
	if err != nil {
		t.Errorf("Execute() after reset error = %v, want nil", err)
	}
}

func TestCircuitBreaker_DefaultConfig(t *testing.T) {
	cb := NewCircuitBreaker(CircuitBreakerConfig{})

	// Check defaults were applied
	if cb.config.MaxFailures <= 0 {
		t.Error("MaxFailures should have default value")
	}
	if cb.config.Timeout <= 0 {
		t.Error("Timeout should have default value")
	}
	if cb.config.MaxHalfOpenRequests <= 0 {
		t.Error("MaxHalfOpenRequests should have default value")
	}
}

func TestCircuitBreakerState_String(t *testing.T) {
	tests := []struct {
		state CircuitBreakerState
		want  string
	}{
		{StateClosed, "CLOSED"},
		{StateOpen, "OPEN"},
		{StateHalfOpen, "HALF_OPEN"},
		{CircuitBreakerState(99), "UNKNOWN"},
	}

	for _, tt := range tests {
		t.Run(tt.want, func(t *testing.T) {
			got := tt.state.String()
			if got != tt.want {
				t.Errorf("String() = %v, want %v", got, tt.want)
			}
		})
	}
}
