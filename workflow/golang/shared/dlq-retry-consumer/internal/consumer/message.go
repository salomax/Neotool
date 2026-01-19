package consumer

import (
	"encoding/json"
	"time"
)

// DLQMessage represents a message in the Dead Letter Queue with metadata
type DLQMessage struct {
	// Original message data
	OriginalMessage json.RawMessage `json:"original_message"` // The actual business message
	OriginalKey     string          `json:"original_key"`     // Message key (e.g., CNPJ)
	OriginalTopic   string          `json:"original_topic"`   // Original Kafka topic

	// Error information
	ErrorMessage string `json:"error_message"` // Human-readable error
	ErrorType    string `json:"error_type"`    // Error classification (e.g., "timeout", "validation_error")
	ErrorCategory string `json:"error_category"` // "transient" or "permanent"

	// Retry tracking
	RetryCount      int        `json:"retry_count"`                // Number of DLQ retry attempts
	FailedTimestamp time.Time  `json:"failed_timestamp"`           // When message first failed
	NextRetryAfter  *time.Time `json:"next_retry_after,omitempty"` // When to retry next (nil = don't retry)

	// Audit trail
	RetryHistory []RetryAttempt `json:"retry_history,omitempty"` // History of all retry attempts
}

// RetryAttempt represents a single retry attempt in the history
type RetryAttempt struct {
	Timestamp    time.Time `json:"timestamp"`     // When retry was attempted
	RetryCount   int       `json:"retry_count"`   // Retry count at this attempt
	ErrorMessage string    `json:"error_message"` // Error that occurred
	ErrorType    string    `json:"error_type"`    // Error classification
	WaitDuration string    `json:"wait_duration"` // How long waited before retry (e.g., "1h")
}

// IsReadyForRetry checks if message is ready to be retried
func (m *DLQMessage) IsReadyForRetry() bool {
	// Permanent errors are never ready
	if m.ErrorCategory == "permanent" {
		return false
	}

	// Check if cooling-off period has elapsed
	if m.NextRetryAfter == nil {
		return false
	}

	return time.Now().After(*m.NextRetryAfter)
}

// ShouldRetry determines if message should be retried based on retry count
func (m *DLQMessage) ShouldRetry(maxRetries int) bool {
	// Permanent errors should not be retried
	if m.ErrorCategory == "permanent" {
		return false
	}

	// Check if retry limit exceeded
	return m.RetryCount < maxRetries
}

// IncrementRetryCount creates a new DLQ message with incremented retry count
func (m *DLQMessage) IncrementRetryCount(err error, errorType string, nextRetryAfter *time.Time) *DLQMessage {
	// Calculate wait duration
	var waitDuration string
	if nextRetryAfter != nil {
		waitDuration = time.Until(*nextRetryAfter).Round(time.Second).String()
	}

	// Create retry attempt record
	attempt := RetryAttempt{
		Timestamp:    time.Now(),
		RetryCount:   m.RetryCount,
		ErrorMessage: err.Error(),
		ErrorType:    errorType,
		WaitDuration: waitDuration,
	}

	// Create new message with updated metadata
	newMsg := &DLQMessage{
		OriginalMessage: m.OriginalMessage,
		OriginalKey:     m.OriginalKey,
		OriginalTopic:   m.OriginalTopic,
		ErrorMessage:    err.Error(),
		ErrorType:       errorType,
		ErrorCategory:   m.ErrorCategory,
		RetryCount:      m.RetryCount + 1,
		FailedTimestamp: m.FailedTimestamp,
		NextRetryAfter:  nextRetryAfter,
		RetryHistory:    append(append([]RetryAttempt{}, m.RetryHistory...), attempt),
	}

	return newMsg
}

// ConvertToPermanent creates a new DLQ message marked as permanent
func (m *DLQMessage) ConvertToPermanent(reason string) *DLQMessage {
	newMsg := &DLQMessage{
		OriginalMessage: m.OriginalMessage,
		OriginalKey:     m.OriginalKey,
		OriginalTopic:   m.OriginalTopic,
		ErrorMessage:    reason,
		ErrorType:       "max_retries_exceeded",
		ErrorCategory:   "permanent",
		RetryCount:      m.RetryCount,
		FailedTimestamp: m.FailedTimestamp,
		NextRetryAfter:  nil,
		RetryHistory:    m.RetryHistory,
	}

	return newMsg
}

// ToJSON serializes the DLQ message to JSON
func (m *DLQMessage) ToJSON() ([]byte, error) {
	return json.Marshal(m)
}

// FromJSON deserializes a DLQ message from JSON
func FromJSON(data []byte) (*DLQMessage, error) {
	var msg DLQMessage
	if err := json.Unmarshal(data, &msg); err != nil {
		return nil, err
	}
	return &msg, nil
}

// GetAge returns how long the message has been in DLQ
func (m *DLQMessage) GetAge() time.Duration {
	return time.Since(m.FailedTimestamp)
}

// GetTotalRetries returns the total number of retry attempts (including history)
func (m *DLQMessage) GetTotalRetries() int {
	return len(m.RetryHistory)
}
