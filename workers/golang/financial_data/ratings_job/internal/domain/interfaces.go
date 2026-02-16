package domain

import (
	"context"

	"github.com/google/uuid"
)

// IssuerClient fetches ratings from an external issuer API
type IssuerClient interface {
	// FetchRatings retrieves ratings for an institution using the mapping config
	FetchRatings(ctx context.Context, mapping InstitutionIssuerMapping) (*FetchResult, error)

	// GetIssuerCode returns the issuer code (e.g., "FITCH")
	GetIssuerCode() string
}

// IssuerClientFactory creates issuer-specific clients
type IssuerClientFactory interface {
	// CreateClient creates a client for the given issuer
	CreateClient(issuer RatingIssuer) (IssuerClient, error)

	// SupportsIssuer checks if the factory supports the given issuer code
	SupportsIssuer(code string) bool
}

// IssuerRepository manages issuer configuration
type IssuerRepository interface {
	// GetEnabled retrieves all enabled issuers
	GetEnabled(ctx context.Context) ([]RatingIssuer, error)

	// GetByCode retrieves an issuer by code
	GetByCode(ctx context.Context, code string) (*RatingIssuer, error)

	// GetByID retrieves an issuer by ID
	GetByID(ctx context.Context, id uuid.UUID) (*RatingIssuer, error)
}

// MappingRepository manages institution-to-issuer mappings
type MappingRepository interface {
	// GetEnabledByIssuer retrieves all enabled mappings for an issuer
	GetEnabledByIssuer(ctx context.Context, issuerID uuid.UUID) ([]InstitutionIssuerMapping, error)

	// ListInstitutionsWithIssuerMapping returns all institutions with their issuer mapping (if any).
	ListInstitutionsWithIssuerMapping(ctx context.Context, issuerID uuid.UUID) ([]InstitutionMapping, error)

	// GetByInstitutionAndIssuer retrieves a specific mapping
	GetByInstitutionAndIssuer(ctx context.Context, institutionID, issuerID uuid.UUID) (*InstitutionIssuerMapping, error)

	// Upsert inserts or updates a mapping
	Upsert(ctx context.Context, mapping *InstitutionIssuerMapping) error

	// CountEnabledByIssuer counts enabled mappings for an issuer
	CountEnabledByIssuer(ctx context.Context, issuerID uuid.UUID) (int, error)
}

// RatingRepository manages rating persistence
type RatingRepository interface {
	// GetByInstitutionAndIssuer retrieves all ratings for an institution from an issuer
	GetByInstitutionAndIssuer(ctx context.Context, institutionID, issuerID uuid.UUID) ([]Rating, error)

	// GetByInstitutionIssuerAndName retrieves a specific rating
	GetByInstitutionIssuerAndName(ctx context.Context, institutionID, issuerID uuid.UUID, ratingName string) (*Rating, error)

	// Upsert inserts or updates a rating, returns true if created (vs updated)
	Upsert(ctx context.Context, rating *Rating) (created bool, err error)

	// UpsertBatch inserts or updates multiple ratings in a transaction
	UpsertBatch(ctx context.Context, ratings []Rating) (created, updated int, err error)
}

// HistoryRepository manages rating history persistence
type HistoryRepository interface {
	// Insert adds a history record
	Insert(ctx context.Context, history RatingHistory) error

	// InsertBatch adds multiple history records in a transaction
	InsertBatch(ctx context.Context, histories []RatingHistory) error

	// GetByInstitution retrieves all history for an institution (ordered by changed_at DESC)
	GetByInstitution(ctx context.Context, institutionID uuid.UUID, limit int) ([]RatingHistory, error)

	// GetByInstitutionAndRatingName retrieves history for a specific rating
	GetByInstitutionAndRatingName(ctx context.Context, institutionID uuid.UUID, ratingName string, limit int) ([]RatingHistory, error)
}

// EvidenceRepository manages evidence persistence
type EvidenceRepository interface {
	// Insert adds a new evidence record
	Insert(ctx context.Context, evidence *SourceEvidence) error

	// GetByHash retrieves evidence by content hash
	GetByHash(ctx context.Context, hash string) (*SourceEvidence, error)

	// Exists checks if evidence with the given hash exists
	Exists(ctx context.Context, hash string) (bool, error)

	// GetByID retrieves evidence by ID
	GetByID(ctx context.Context, id uuid.UUID) (*SourceEvidence, error)
}
