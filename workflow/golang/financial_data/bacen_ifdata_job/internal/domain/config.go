package domain

// ExtractionConfig holds extraction-specific configuration
type ExtractionConfig struct {
	MaxWorkers          int
	MaxIterations       int
	PageSize            int
	MaxTaskRetries      int
	TaskPollIntervalDur int64 // Duration in milliseconds
}
