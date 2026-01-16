package consumer

import (
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDLQMessage_IsReadyForRetry(t *testing.T) {
	now := time.Now()
	past := now.Add(-1 * time.Hour)
	future := now.Add(1 * time.Hour)

	tests := []struct {
		name     string
		msg      DLQMessage
		expected bool
	}{
		{
			name: "transient with past retry time",
			msg: DLQMessage{
				ErrorCategory:  "transient",
				NextRetryAfter: &past,
			},
			expected: true,
		},
		{
			name: "transient with future retry time",
			msg: DLQMessage{
				ErrorCategory:  "transient",
				NextRetryAfter: &future,
			},
			expected: false,
		},
		{
			name: "permanent error",
			msg: DLQMessage{
				ErrorCategory:  "permanent",
				NextRetryAfter: &past,
			},
			expected: false,
		},
		{
			name: "nil next retry time",
			msg: DLQMessage{
				ErrorCategory:  "transient",
				NextRetryAfter: nil,
			},
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := tt.msg.IsReadyForRetry()
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestDLQMessage_ShouldRetry(t *testing.T) {
	tests := []struct {
		name       string
		msg        DLQMessage
		maxRetries int
		expected   bool
	}{
		{
			name: "under retry limit",
			msg: DLQMessage{
				ErrorCategory: "transient",
				RetryCount:    1,
			},
			maxRetries: 3,
			expected:   true,
		},
		{
			name: "at retry limit",
			msg: DLQMessage{
				ErrorCategory: "transient",
				RetryCount:    3,
			},
			maxRetries: 3,
			expected:   false,
		},
		{
			name: "exceeded retry limit",
			msg: DLQMessage{
				ErrorCategory: "transient",
				RetryCount:    5,
			},
			maxRetries: 3,
			expected:   false,
		},
		{
			name: "permanent error",
			msg: DLQMessage{
				ErrorCategory: "permanent",
				RetryCount:    0,
			},
			maxRetries: 3,
			expected:   false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := tt.msg.ShouldRetry(tt.maxRetries)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestDLQMessage_IncrementRetryCount(t *testing.T) {
	originalMsg := json.RawMessage(`{"test": "data"}`)
	nextRetry := time.Now().Add(1 * time.Hour)

	msg := &DLQMessage{
		OriginalMessage: originalMsg,
		OriginalKey:     "test-key",
		OriginalTopic:   "test.topic",
		ErrorCategory:   "transient",
		RetryCount:      0,
		FailedTimestamp: time.Now().Add(-1 * time.Hour),
		RetryHistory:    []RetryAttempt{},
	}

	err := errors.New("test error")
	newMsg := msg.IncrementRetryCount(err, "timeout", &nextRetry)

	// Check retry count incremented
	assert.Equal(t, 1, newMsg.RetryCount)

	// Check original data preserved
	assert.Equal(t, originalMsg, newMsg.OriginalMessage)
	assert.Equal(t, "test-key", newMsg.OriginalKey)
	assert.Equal(t, "test.topic", newMsg.OriginalTopic)

	// Check error updated
	assert.Equal(t, "test error", newMsg.ErrorMessage)
	assert.Equal(t, "timeout", newMsg.ErrorType)

	// Check retry history
	assert.Len(t, newMsg.RetryHistory, 1)
	assert.Equal(t, 0, newMsg.RetryHistory[0].RetryCount)
	assert.Equal(t, "test error", newMsg.RetryHistory[0].ErrorMessage)
	assert.Equal(t, "timeout", newMsg.RetryHistory[0].ErrorType)

	// Check next retry time
	assert.NotNil(t, newMsg.NextRetryAfter)
	assert.Equal(t, nextRetry, *newMsg.NextRetryAfter)
}

func TestDLQMessage_ConvertToPermanent(t *testing.T) {
	msg := &DLQMessage{
		OriginalMessage: json.RawMessage(`{"test": "data"}`),
		OriginalKey:     "test-key",
		OriginalTopic:   "test.topic",
		ErrorCategory:   "transient",
		RetryCount:      3,
		ErrorMessage:    "timeout",
		RetryHistory: []RetryAttempt{
			{RetryCount: 0, ErrorMessage: "timeout 1"},
			{RetryCount: 1, ErrorMessage: "timeout 2"},
		},
	}

	reason := "Exceeded maximum retry attempts (3)"
	permanent := msg.ConvertToPermanent(reason)

	assert.Equal(t, "permanent", permanent.ErrorCategory)
	assert.Equal(t, reason, permanent.ErrorMessage)
	assert.Equal(t, "max_retries_exceeded", permanent.ErrorType)
	assert.Nil(t, permanent.NextRetryAfter)

	// Original data preserved
	assert.Equal(t, msg.OriginalMessage, permanent.OriginalMessage)
	assert.Equal(t, msg.RetryCount, permanent.RetryCount)
	assert.Equal(t, msg.RetryHistory, permanent.RetryHistory)
}

func TestDLQMessage_ToJSON_FromJSON(t *testing.T) {
	now := time.Now().Round(time.Second) // Round to avoid precision issues
	nextRetry := now.Add(1 * time.Hour)

	original := &DLQMessage{
		OriginalMessage: json.RawMessage(`{"cnpj": "12345678000190"}`),
		OriginalKey:     "12345678000190",
		OriginalTopic:   "financial-data.institution.upserted",
		ErrorMessage:    "timeout error",
		ErrorType:       "timeout",
		ErrorCategory:   "transient",
		RetryCount:      1,
		FailedTimestamp: now,
		NextRetryAfter:  &nextRetry,
		RetryHistory: []RetryAttempt{
			{
				Timestamp:    now,
				RetryCount:   0,
				ErrorMessage: "initial timeout",
				ErrorType:    "timeout",
				WaitDuration: "1h",
			},
		},
	}

	// Serialize
	data, err := original.ToJSON()
	require.NoError(t, err)

	// Deserialize
	restored, err := FromJSON(data)
	require.NoError(t, err)

	// Compare
	assert.Equal(t, original.OriginalKey, restored.OriginalKey)
	assert.Equal(t, original.OriginalTopic, restored.OriginalTopic)
	assert.Equal(t, original.ErrorMessage, restored.ErrorMessage)
	assert.Equal(t, original.ErrorType, restored.ErrorType)
	assert.Equal(t, original.ErrorCategory, restored.ErrorCategory)
	assert.Equal(t, original.RetryCount, restored.RetryCount)
	assert.Equal(t, original.FailedTimestamp.Unix(), restored.FailedTimestamp.Unix())

	require.NotNil(t, restored.NextRetryAfter)
	assert.Equal(t, nextRetry.Unix(), restored.NextRetryAfter.Unix())

	assert.Len(t, restored.RetryHistory, 1)
	assert.Equal(t, "initial timeout", restored.RetryHistory[0].ErrorMessage)
}

func TestDLQMessage_GetAge(t *testing.T) {
	failedTime := time.Now().Add(-2 * time.Hour)
	msg := &DLQMessage{
		FailedTimestamp: failedTime,
	}

	age := msg.GetAge()
	assert.InDelta(t, 2*time.Hour, age, float64(1*time.Second))
}

func TestDLQMessage_GetTotalRetries(t *testing.T) {
	msg := &DLQMessage{
		RetryHistory: []RetryAttempt{
			{RetryCount: 0},
			{RetryCount: 1},
			{RetryCount: 2},
		},
	}

	assert.Equal(t, 3, msg.GetTotalRetries())
}

func TestDLQMessage_RetryHistoryPreserved(t *testing.T) {
	msg := &DLQMessage{
		OriginalMessage: json.RawMessage(`{}`),
		OriginalKey:     "key",
		OriginalTopic:   "topic",
		ErrorCategory:   "transient",
		RetryCount:      0,
		FailedTimestamp: time.Now(),
		RetryHistory:    []RetryAttempt{},
	}

	// Simulate multiple retries
	nextRetry1 := time.Now().Add(1 * time.Hour)
	msg = msg.IncrementRetryCount(errors.New("error 1"), "timeout", &nextRetry1)

	nextRetry2 := time.Now().Add(6 * time.Hour)
	msg = msg.IncrementRetryCount(errors.New("error 2"), "timeout", &nextRetry2)

	nextRetry3 := time.Now().Add(24 * time.Hour)
	msg = msg.IncrementRetryCount(errors.New("error 3"), "timeout", &nextRetry3)

	// Verify history
	assert.Equal(t, 3, msg.RetryCount)
	assert.Len(t, msg.RetryHistory, 3)

	assert.Equal(t, 0, msg.RetryHistory[0].RetryCount)
	assert.Equal(t, "error 1", msg.RetryHistory[0].ErrorMessage)

	assert.Equal(t, 1, msg.RetryHistory[1].RetryCount)
	assert.Equal(t, "error 2", msg.RetryHistory[1].ErrorMessage)

	assert.Equal(t, 2, msg.RetryHistory[2].RetryCount)
	assert.Equal(t, "error 3", msg.RetryHistory[2].ErrorMessage)
}
