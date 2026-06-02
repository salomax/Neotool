package domain

import (
	"time"

	"github.com/google/uuid"
)

type MarketDataJobSucceededEvent struct {
	EventID      uuid.UUID `json:"eventId"`
	JobName      string    `json:"jobName"`
	RunID        uuid.UUID `json:"runId"`
	FromDate     string    `json:"fromDate"`     // YYYY-MM-DD
	ToDate       string    `json:"toDate"`       // YYYY-MM-DD
	LastDataDate string    `json:"lastDataDate"` // YYYY-MM-DD
	LastRunAt    time.Time `json:"lastRunAt"`
	Status       string    `json:"status"`
	IsBackfill   bool      `json:"isBackfill"`
	PublishedAt  time.Time `json:"publishedAt"`
}
