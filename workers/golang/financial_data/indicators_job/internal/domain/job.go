package domain

import (
	"time"

	"github.com/google/uuid"
)

const JobName = "indicators"

type MarketDataJobSucceededEvent struct {
	EventID      uuid.UUID `json:"eventId"`
	JobName      string    `json:"jobName"`
	RunID        uuid.UUID `json:"runId"`
	FromDate     time.Time `json:"fromDate"`
	ToDate       time.Time `json:"toDate"`
	LastDataDate time.Time `json:"lastDataDate"`
	LastRunAt    time.Time `json:"lastRunAt"`
	Status       string    `json:"status"`
	IsBackfill   bool      `json:"isBackfill"`
	PublishedAt  time.Time `json:"publishedAt"`
}
