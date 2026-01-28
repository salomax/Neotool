package domain

import (
	"fmt"
	"time"

	"github.com/google/uuid"
)

// RatingIssuer represents a rating agency (Fitch, S&P Global, Moody's)
type RatingIssuer struct {
	ID          uuid.UUID    `json:"id"`
	Code        string       `json:"code"`
	DisplayName string       `json:"display_name"`
	Website     string       `json:"website"`
	Config      IssuerConfig `json:"config"`
	LogoAssetID *uuid.UUID   `json:"logo_asset_id,omitempty"`
	Enabled     bool         `json:"enabled"`
	CreatedAt   time.Time    `json:"created_at"`
	UpdatedAt   time.Time    `json:"updated_at"`
}

// IssuerConfig holds issuer-specific configuration from JSONB
type IssuerConfig struct {
	APIURL           string `json:"api_url,omitempty"`
	URLBase          string `json:"url_base,omitempty"`
	ThrottleMs       int    `json:"throttle_ms,omitempty"`
	GraphQLOperation string `json:"graphql_operation,omitempty"`
	IdentifierField  string `json:"identifier_field,omitempty"`
}

// FinancialInstitution represents a stored financial institution.
type FinancialInstitution struct {
	ID        uuid.UUID `json:"id"`
	LegalName string    `json:"legal_name"`
}

// InstitutionMapping pairs a financial institution with an optional issuer mapping.
type InstitutionMapping struct {
	Institution FinancialInstitution
	Mapping     *InstitutionIssuerMapping
}

// InstitutionIssuerMapping maps a financial institution to an issuer with identifiers
type InstitutionIssuerMapping struct {
	ID                     uuid.UUID     `json:"id"`
	RatingIssuerID         uuid.UUID     `json:"rating_issuer_id"`
	FinancialInstitutionID uuid.UUID     `json:"financial_institution_id"`
	Config                 MappingConfig `json:"config"`
	Enabled                bool          `json:"enabled"`
	CreatedAt              time.Time     `json:"created_at"`
	UpdatedAt              time.Time     `json:"updated_at"`
}

// MappingConfig holds institution-specific identifiers for an issuer
type MappingConfig struct {
	// Fitch uses slug (e.g., "banco-do-brasil-sa-80089721")
	Slug string `json:"slug,omitempty"`
	// S&P uses entity_id
	EntityID string `json:"entity_id,omitempty"`
	// Moody's uses org_id
	OrgID string `json:"org_id,omitempty"`
}

// Rating represents a financial institution rating
type Rating struct {
	ID                     uuid.UUID              `json:"id"`
	FinancialInstitutionID uuid.UUID              `json:"financial_institution_id"`
	IssuerID               uuid.UUID              `json:"issuer_id"`
	RatingName             string                 `json:"rating_name"`
	RatingLabel            string                 `json:"rating_label"`
	RatingDate             time.Time              `json:"rating_date"`
	CustomParams           map[string]interface{} `json:"custom_params"`
	SourceEvidenceID       *uuid.UUID             `json:"source_evidence_id,omitempty"`
	ContentHash            string                 `json:"content_hash"`
	CreatedAt              time.Time              `json:"created_at"`
	UpdatedAt              time.Time              `json:"updated_at"`
}

// RatingHistory represents a historical rating record
type RatingHistory struct {
	ID                     uuid.UUID              `json:"id"`
	FinancialInstitutionID uuid.UUID              `json:"financial_institution_id"`
	IssuerID               uuid.UUID              `json:"issuer_id"`
	RatingName             string                 `json:"rating_name"`
	RatingLabel            string                 `json:"rating_label"`
	RatingDate             time.Time              `json:"rating_date"`
	CustomParams           map[string]interface{} `json:"custom_params"`
	SourceEvidenceID       *uuid.UUID             `json:"source_evidence_id,omitempty"`
	ContentHash            string                 `json:"content_hash"`
	ChangedAt              time.Time              `json:"changed_at"`
}

// SourceEvidence stores the raw API response for audit
type SourceEvidence struct {
	ID          uuid.UUID        `json:"id"`
	IssuerID    uuid.UUID        `json:"issuer_id"`
	FetchedAt   time.Time        `json:"fetched_at"`
	Metadata    EvidenceMetadata `json:"metadata"`
	RawPayload  string           `json:"raw_payload"`
	ContentHash string           `json:"content_hash"`
	CreatedAt   time.Time        `json:"created_at"`
}

// EvidenceMetadata contains request/response metadata
type EvidenceMetadata struct {
	URL         string `json:"url"`
	HTTPStatus  int    `json:"http_status"`
	ContentType string `json:"content_type,omitempty"`
	RequestID   string `json:"request_id,omitempty"`
	Slug        string `json:"slug,omitempty"`
}

// ChangeType indicates the type of change detected
type ChangeType string

const (
	ChangeTypeCreated   ChangeType = "CREATED"
	ChangeTypeUpdated   ChangeType = "UPDATED"
	ChangeTypeUnchanged ChangeType = "UNCHANGED"
)

// RatingChange represents a detected change in a rating
type RatingChange struct {
	Rating       Rating
	ChangeType   ChangeType
	PreviousHash string
}

// FetchResult represents the result of fetching ratings from an issuer
type FetchResult struct {
	InstitutionID uuid.UUID
	IssuerID      uuid.UUID
	Ratings       []Rating
	Evidence      SourceEvidence
	Error         error
}

// FetchError carries evidence for failed fetch attempts.
type FetchError struct {
	Err      error
	Evidence *SourceEvidence
}

func (e FetchError) Error() string {
	if e.Err != nil {
		return e.Err.Error()
	}
	return "fetch error"
}

func (e FetchError) Unwrap() error {
	return e.Err
}

// IngestionStats tracks statistics for an ingestion run
type IngestionStats struct {
	IssuersProcessed      int
	InstitutionsProcessed int
	RatingsCreated        int
	RatingsUpdated        int
	RatingsUnchanged      int
	EvidenceStored        int
	Errors                int
	Duration              time.Duration
}

// String returns a human-readable summary of the stats
func (s IngestionStats) String() string {
	return fmt.Sprintf(
		"issuers=%d institutions=%d created=%d updated=%d unchanged=%d evidence=%d errors=%d duration=%s",
		s.IssuersProcessed,
		s.InstitutionsProcessed,
		s.RatingsCreated,
		s.RatingsUpdated,
		s.RatingsUnchanged,
		s.EvidenceStored,
		s.Errors,
		s.Duration,
	)
}
