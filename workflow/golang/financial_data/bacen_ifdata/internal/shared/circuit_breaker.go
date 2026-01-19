package shared

import (
	"errors"
	"sync"
	"time"
)

var (
	// ErrCircuitOpen is returned when the circuit breaker is open
	ErrCircuitOpen = errors.New("circuit breaker is open")
)

// CircuitBreakerState represents the state of the circuit breaker
type CircuitBreakerState int

const (
	// StateClosed allows requests to pass through
	StateClosed CircuitBreakerState = iota
	// StateOpen blocks all requests
	StateOpen
	// StateHalfOpen allows a limited number of test requests
	StateHalfOpen
)

func (s CircuitBreakerState) String() string {
	switch s {
	case StateClosed:
		return "CLOSED"
	case StateOpen:
		return "OPEN"
	case StateHalfOpen:
		return "HALF_OPEN"
	default:
		return "UNKNOWN"
	}
}

// CircuitBreakerConfig holds circuit breaker configuration
type CircuitBreakerConfig struct {
	// MaxFailures is the number of consecutive failures before opening
	MaxFailures int
	// Timeout is how long to wait before transitioning from Open to Half-Open
	Timeout time.Duration
	// MaxHalfOpenRequests is the number of test requests allowed in Half-Open state
	MaxHalfOpenRequests int
}

// CircuitBreaker implements the circuit breaker pattern
type CircuitBreaker struct {
	config              CircuitBreakerConfig
	state               CircuitBreakerState
	consecutiveFailures int
	lastFailureTime     time.Time
	halfOpenSuccesses   int
	halfOpenAttempts    int
	mu                  sync.RWMutex
}

// NewCircuitBreaker creates a new circuit breaker
func NewCircuitBreaker(config CircuitBreakerConfig) *CircuitBreaker {
	if config.MaxFailures <= 0 {
		config.MaxFailures = 5
	}
	if config.Timeout <= 0 {
		config.Timeout = 60 * time.Second
	}
	if config.MaxHalfOpenRequests <= 0 {
		config.MaxHalfOpenRequests = 3
	}

	return &CircuitBreaker{
		config: config,
		state:  StateClosed,
	}
}

// Execute wraps an operation with circuit breaker logic
func (cb *CircuitBreaker) Execute(operation func() error) error {
	if !cb.canAttempt() {
		return ErrCircuitOpen
	}

	err := operation()

	cb.recordResult(err)
	return err
}

// canAttempt checks if a request can be attempted
func (cb *CircuitBreaker) canAttempt() bool {
	cb.mu.Lock()
	defer cb.mu.Unlock()

	switch cb.state {
	case StateClosed:
		return true

	case StateOpen:
		// Check if timeout has elapsed
		if time.Since(cb.lastFailureTime) > cb.config.Timeout {
			// Transition to Half-Open
			cb.state = StateHalfOpen
			cb.halfOpenSuccesses = 0
			cb.halfOpenAttempts = 0
			return true
		}
		return false

	case StateHalfOpen:
		// Allow limited test requests
		return cb.halfOpenAttempts < cb.config.MaxHalfOpenRequests

	default:
		return false
	}
}

// recordResult records the result of an operation
func (cb *CircuitBreaker) recordResult(err error) {
	cb.mu.Lock()
	defer cb.mu.Unlock()

	switch cb.state {
	case StateClosed:
		if err != nil {
			cb.consecutiveFailures++
			cb.lastFailureTime = time.Now()

			if cb.consecutiveFailures >= cb.config.MaxFailures {
				// Transition to Open
				cb.state = StateOpen
			}
		} else {
			// Success - reset counter
			cb.consecutiveFailures = 0
		}

	case StateHalfOpen:
		cb.halfOpenAttempts++

		if err != nil {
			// Failed in half-open state - reopen circuit
			cb.state = StateOpen
			cb.lastFailureTime = time.Now()
			cb.consecutiveFailures = cb.config.MaxFailures
		} else {
			cb.halfOpenSuccesses++

			// If we've had enough successful test requests, close the circuit
			if cb.halfOpenSuccesses >= cb.config.MaxHalfOpenRequests {
				cb.state = StateClosed
				cb.consecutiveFailures = 0
			}
		}
	}
}

// GetState returns the current state of the circuit breaker
func (cb *CircuitBreaker) GetState() CircuitBreakerState {
	cb.mu.RLock()
	defer cb.mu.RUnlock()
	return cb.state
}

// GetConsecutiveFailures returns the current failure count
func (cb *CircuitBreaker) GetConsecutiveFailures() int {
	cb.mu.RLock()
	defer cb.mu.RUnlock()
	return cb.consecutiveFailures
}

// Reset manually resets the circuit breaker to closed state
func (cb *CircuitBreaker) Reset() {
	cb.mu.Lock()
	defer cb.mu.Unlock()

	cb.state = StateClosed
	cb.consecutiveFailures = 0
	cb.halfOpenSuccesses = 0
	cb.halfOpenAttempts = 0
}
