package domain

import "time"

// IndicatorVariation represents a single measure of an indicator
// (joined with indicator metadata for source resolution)
type IndicatorVariation struct {
	ID                string
	IndicatorID       string
	IndicatorLabel    string
	IndicatorIssuer   string
	IndicatorFrequency string
	Label             string
	Unit              string
	IsDefault         bool
	IncrementalMonths *int
	IncrementalYears  *int
	IncrementalDays   *int
}

// LoadStatus represents a load control status
type LoadStatus string

const (
	LoadStatusPending LoadStatus = "PENDING"
	LoadStatusRunning LoadStatus = "RUNNING"
	LoadStatusSuccess LoadStatus = "SUCCESS"
	LoadStatusFailed  LoadStatus = "FAILED"
	LoadStatusSkipped LoadStatus = "SKIPPED"
)

// RunType represents the type of run
type RunType string

const (
	RunTypeFull        RunType = "FULL"
	RunTypeIncremental RunType = "INCREMENTAL"
	RunTypeBackfill    RunType = "BACKFILL"
)

// LoadControl represents a control record for a run
type LoadControl struct {
	ID                   string
	IndicatorVariationID string
	RunType              RunType
	Status               LoadStatus
	ScheduledAt          time.Time
	StartedAt            *time.Time
	FinishedAt           *time.Time
	LastReferenceDate    *time.Time
	LastPublicationDate  *time.Time
	Cursor               map[string]any
	ErrorMessage         *string
	Attempts             int
}

// ValueRecord represents a normalized indicator value
type ValueRecord struct {
	IndicatorVariationID string
	ReferenceDate        time.Time
	PublicationDate      *time.Time
	Value                string
	AdjustedValue        *string
	SourceMeta           map[string]any
}
