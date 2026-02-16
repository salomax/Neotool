package domain

import "errors"

// Domain errors
var (
	// ErrIssuerNotFound is returned when an issuer is not found
	ErrIssuerNotFound = errors.New("issuer not found")

	// ErrMappingNotFound is returned when an institution-issuer mapping is not found
	ErrMappingNotFound = errors.New("institution-issuer mapping not found")

	// ErrRatingNotFound is returned when a rating is not found
	ErrRatingNotFound = errors.New("rating not found")

	// ErrEvidenceNotFound is returned when evidence is not found
	ErrEvidenceNotFound = errors.New("evidence not found")

	// ErrInvalidSlug is returned when a Fitch slug is invalid or missing
	ErrInvalidSlug = errors.New("invalid or missing slug in mapping config")

	// ErrEntityNotFound is returned when an entity is not found in the API response
	ErrEntityNotFound = errors.New("entity not found in API response")

	// ErrGraphQLError is returned when the GraphQL API returns an error
	ErrGraphQLError = errors.New("GraphQL API error")

	// ErrRateLimited is returned when the API rate limit is exceeded
	ErrRateLimited = errors.New("rate limit exceeded")

	// ErrNoEnabledIssuers is returned when no enabled issuers are found
	ErrNoEnabledIssuers = errors.New("no enabled issuers found")

	// ErrNoEnabledMappings is returned when no enabled mappings are found for an issuer
	ErrNoEnabledMappings = errors.New("no enabled mappings found for issuer")
)
