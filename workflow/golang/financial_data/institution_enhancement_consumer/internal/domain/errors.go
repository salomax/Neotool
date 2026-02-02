package domain

import "errors"

// Domain-level errors
var (
	// ErrInstitutionNotFound is returned when institution is not found in CNPJá API
	ErrInstitutionNotFound = errors.New("institution not found")

	// ErrWebsiteUnreachable is returned when website validation fails
	ErrWebsiteUnreachable = errors.New("website is unreachable")

	// ErrRateLimitExceeded is returned when CNPJá API rate limit is exceeded
	ErrRateLimitExceeded = errors.New("rate limit exceeded")

	// ErrProcessingFailed is returned when message processing fails
	ErrProcessingFailed = errors.New("message processing failed")
)
