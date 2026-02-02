package domain

import "errors"

var (
	// ErrNoDataAvailable indicates no data is available for processing
	ErrNoDataAvailable = errors.New("no data available for the target quarter")

	// ErrPublicationWindowNotReached indicates the publication window hasn't been reached yet
	ErrPublicationWindowNotReached = errors.New("publication window for target quarter not yet reached")

	// ErrInvalidAnoMes indicates an invalid ano_mes format
	ErrInvalidAnoMes = errors.New("invalid ano_mes format, must be YYYYMM")

	// ErrInvalidQuarter indicates the month is not a quarter end (03, 06, 09, 12)
	ErrInvalidQuarter = errors.New("invalid quarter, month must be 03, 06, 09, or 12")

	// ErrInvalidCSVFormat indicates CSV data doesn't match expected format
	ErrInvalidCSVFormat = errors.New("invalid CSV format from BACEN API")

	// ErrExtractTaskNotFound indicates an extract task was not found
	ErrExtractTaskNotFound = errors.New("extract task not found")

	// ErrInstitutionNotFound indicates an institution was not found
	ErrInstitutionNotFound = errors.New("institution not found")

	// ErrMaxRetriesExceeded indicates maximum retry attempts exceeded
	ErrMaxRetriesExceeded = errors.New("maximum retry attempts exceeded")
)
