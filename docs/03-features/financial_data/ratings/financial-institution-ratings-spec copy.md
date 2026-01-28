# Financial Institution Ratings Ingestion - Technical Specification

**Type**: Backend + Workflow
**Module**: financial_data
**Status**: Specification
**Feature Doc**: [financial-institution-ratings.md](financial-institution-ratings.md)

---

## 1. Overview

This specification details the implementation of a ratings ingestion system for financial institutions. The system fetches ratings from external issuers (Fitch, S&P Global, Moody's), stores them with full audit trails, and exposes them via the existing GraphQL API.

### Scope

- **Phase 1**: Fitch ratings ingestion only
- **Future Phases**: S&P Global, Moody's, and other issuers

### Key Characteristics

- Idempotent processing with hash-based change detection
- Evidence storage for audit compliance
- Rate-limited API access (1 request per 15 seconds for Fitch)
- History tracking for all rating changes

---

## 2. Database Schema

### 2.1 Migration File

**File**: `service/kotlin/financialdata/src/main/resources/db/migration/V1_6__create_rating_tables.sql`

```sql
-- =============================================================================
-- Financial Institution Ratings Tables
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: rating_issuer
-- Stores rating agency metadata and configuration
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS financial_data.rating_issuer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    website         VARCHAR(500),
    config          JSONB NOT NULL DEFAULT '{}',
    logo_asset_id   UUID,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE financial_data.rating_issuer IS 'Rating agencies (Fitch, S&P Global, Moody''s, etc.)';
COMMENT ON COLUMN financial_data.rating_issuer.code IS 'Unique identifier code (e.g., FITCH, SP_GLOBAL, MOODYS)';
COMMENT ON COLUMN financial_data.rating_issuer.config IS 'Issuer-specific config: {"url_base": "...", "throttle_ms": 15000}';

CREATE INDEX IF NOT EXISTS idx_rating_issuer_code ON financial_data.rating_issuer(code);
CREATE INDEX IF NOT EXISTS idx_rating_issuer_enabled ON financial_data.rating_issuer(enabled) WHERE enabled = true;

-- -----------------------------------------------------------------------------
-- Table: financial_institution_rating_issuer
-- Maps financial institutions to issuer-specific identifiers
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS financial_data.financial_institution_rating_issuer (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rating_issuer_id            UUID NOT NULL REFERENCES financial_data.rating_issuer(id),
    financial_institution_id    UUID NOT NULL REFERENCES financial_data.financial_institution(id),
    config                      JSONB NOT NULL DEFAULT '{}',
    enabled                     BOOLEAN NOT NULL DEFAULT true,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(rating_issuer_id, financial_institution_id)
);

COMMENT ON TABLE financial_data.financial_institution_rating_issuer IS 'Institution-to-issuer mapping with issuer-specific identifiers';
COMMENT ON COLUMN financial_data.financial_institution_rating_issuer.config IS 'Issuer-specific identifiers: {"slug": "banco-do-brasil-sa-80089721"} for Fitch';

CREATE INDEX IF NOT EXISTS idx_fi_rating_issuer_issuer ON financial_data.financial_institution_rating_issuer(rating_issuer_id);
CREATE INDEX IF NOT EXISTS idx_fi_rating_issuer_institution ON financial_data.financial_institution_rating_issuer(financial_institution_id);
CREATE INDEX IF NOT EXISTS idx_fi_rating_issuer_enabled ON financial_data.financial_institution_rating_issuer(enabled) WHERE enabled = true;

-- -----------------------------------------------------------------------------
-- Table: rating_source_evidence
-- Stores raw API responses for audit and troubleshooting
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS financial_data.rating_source_evidence (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issuer_id       UUID NOT NULL REFERENCES financial_data.rating_issuer(id),
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata        JSONB NOT NULL DEFAULT '{}',
    raw_payload     TEXT NOT NULL,
    content_hash    CHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE financial_data.rating_source_evidence IS 'Raw API responses stored for audit compliance';
COMMENT ON COLUMN financial_data.rating_source_evidence.metadata IS 'Request metadata: {"url": "...", "http_status": 200, "content_type": "application/json", "request_id": "..."}';
COMMENT ON COLUMN financial_data.rating_source_evidence.content_hash IS 'SHA-256 hash of raw_payload for deduplication';

CREATE INDEX IF NOT EXISTS idx_rating_source_evidence_issuer ON financial_data.rating_source_evidence(issuer_id);
CREATE INDEX IF NOT EXISTS idx_rating_source_evidence_fetched ON financial_data.rating_source_evidence(fetched_at);
CREATE INDEX IF NOT EXISTS idx_rating_source_evidence_hash ON financial_data.rating_source_evidence(content_hash);

-- -----------------------------------------------------------------------------
-- Table: financial_institution_rating
-- Current ratings for each institution
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS financial_data.financial_institution_rating (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_institution_id    UUID NOT NULL REFERENCES financial_data.financial_institution(id),
    issuer_id                   UUID NOT NULL REFERENCES financial_data.rating_issuer(id),
    name                        VARCHAR(255) NOT NULL,
    label                       VARCHAR(100) NOT NULL,
    rating_date                 DATE NOT NULL,
    custom_params               JSONB NOT NULL DEFAULT '{}',
    source_evidence_id          UUID REFERENCES financial_data.rating_source_evidence(id),
    content_hash                CHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(financial_institution_id, issuer_id, name)
);

COMMENT ON TABLE financial_data.financial_institution_rating IS 'Current ratings for financial institutions';
COMMENT ON COLUMN financial_data.financial_institution_rating.name IS 'Rating type in UPPER_SNAKE_CASE (e.g., LONG_TERM_ISSUER_DEFAULT_RATING)';
COMMENT ON COLUMN financial_data.financial_institution_rating.label IS 'Rating value (e.g., AAA(bra), BB, F1+)';
COMMENT ON COLUMN financial_data.financial_institution_rating.custom_params IS 'Issuer-specific fields: {"action": "Affirmed", "alert_code": "RO:Sta", "fitch_id": "129697998"}';
COMMENT ON COLUMN financial_data.financial_institution_rating.content_hash IS 'SHA-256 hash for idempotency detection';

CREATE INDEX IF NOT EXISTS idx_fi_rating_institution ON financial_data.financial_institution_rating(financial_institution_id);
CREATE INDEX IF NOT EXISTS idx_fi_rating_issuer ON financial_data.financial_institution_rating(issuer_id);
CREATE INDEX IF NOT EXISTS idx_fi_rating_institution_issuer ON financial_data.financial_institution_rating(financial_institution_id, issuer_id);
CREATE INDEX IF NOT EXISTS idx_fi_rating_name ON financial_data.financial_institution_rating(name);
CREATE INDEX IF NOT EXISTS idx_fi_rating_date ON financial_data.financial_institution_rating(rating_date);

-- -----------------------------------------------------------------------------
-- Table: financial_institution_rating_history
-- Audit trail for all rating changes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS financial_data.financial_institution_rating_history (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_institution_id    UUID NOT NULL REFERENCES financial_data.financial_institution(id),
    issuer_id                   UUID NOT NULL REFERENCES financial_data.rating_issuer(id),
    name                        VARCHAR(255) NOT NULL,
    label                       VARCHAR(100) NOT NULL,
    rating_date                 DATE NOT NULL,
    custom_params               JSONB NOT NULL DEFAULT '{}',
    source_evidence_id          UUID REFERENCES financial_data.rating_source_evidence(id),
    content_hash                CHAR(64) NOT NULL,
    changed_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE financial_data.financial_institution_rating_history IS 'Historical record of all rating changes';

CREATE INDEX IF NOT EXISTS idx_fi_rating_history_institution ON financial_data.financial_institution_rating_history(financial_institution_id);
CREATE INDEX IF NOT EXISTS idx_fi_rating_history_issuer ON financial_data.financial_institution_rating_history(issuer_id);
CREATE INDEX IF NOT EXISTS idx_fi_rating_history_changed ON financial_data.financial_institution_rating_history(changed_at);
CREATE INDEX IF NOT EXISTS idx_fi_rating_history_name ON financial_data.financial_institution_rating_history(name);

-- -----------------------------------------------------------------------------
-- Trigger: Auto-update updated_at timestamp
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION financial_data.update_rating_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_rating_issuer_updated
    BEFORE UPDATE ON financial_data.rating_issuer
    FOR EACH ROW EXECUTE FUNCTION financial_data.update_rating_timestamp();

CREATE TRIGGER tr_fi_rating_issuer_updated
    BEFORE UPDATE ON financial_data.financial_institution_rating_issuer
    FOR EACH ROW EXECUTE FUNCTION financial_data.update_rating_timestamp();

CREATE TRIGGER tr_fi_rating_updated
    BEFORE UPDATE ON financial_data.financial_institution_rating
    FOR EACH ROW EXECUTE FUNCTION financial_data.update_rating_timestamp();
```

### 2.2 Seed Data

**File**: `service/kotlin/financialdata/src/main/resources/db/migration/V1_7__seed_rating_issuers.sql`

```sql
-- =============================================================================
-- Seed Data: Rating Issuers
-- =============================================================================

INSERT INTO financial_data.rating_issuer (code, name, website, config, enabled)
VALUES (
    'FITCH',
    'Fitch Ratings',
    'https://www.fitchratings.com',
    '{
        "api_url": "https://api.fitchratings.com/",
        "url_base": "https://www.fitchratings.com/entity/",
        "throttle_ms": 15000,
        "graphql_operation": "Entity",
        "identifier_field": "slug"
    }'::jsonb,
    true
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    website = EXCLUDED.website,
    config = EXCLUDED.config,
    updated_at = NOW();

-- Future issuers (disabled by default)
INSERT INTO financial_data.rating_issuer (code, name, website, config, enabled)
VALUES
    ('SP_GLOBAL', 'S&P Global Ratings', 'https://www.spglobal.com/ratings', '{"identifier_field": "entity_id"}'::jsonb, false),
    ('MOODYS', 'Moody''s Investors Service', 'https://www.moodys.com', '{"identifier_field": "org_id"}'::jsonb, false)
ON CONFLICT (code) DO NOTHING;
```

---

## 3. Go Workflow Implementation

### 3.1 Directory Structure

```
workflow/golang/financial_data/ratings/
├── cmd/
│   ├── cronjob/
│   │   └── main.go                 # Scheduled execution entry point
│   └── cli/
│       └── main.go                 # Manual execution with flags
├── internal/
│   ├── domain/
│   │   ├── models.go               # Rating, Evidence, Issuer models
│   │   ├── interfaces.go           # Repository and client interfaces
│   │   ├── config.go               # Configuration types
│   │   └── errors.go               # Domain-specific errors
│   ├── application/
│   │   ├── orchestrator.go         # Main job orchestration
│   │   └── config_loader.go        # Environment config loader
│   ├── infrastructure/
│   │   ├── database/
│   │   │   ├── postgres.go         # Connection pool setup
│   │   │   ├── rating_repository.go
│   │   │   ├── evidence_repository.go
│   │   │   ├── issuer_repository.go
│   │   │   └── history_repository.go
│   │   └── issuer/
│   │       ├── fitch/
│   │       │   ├── client.go       # Fitch GraphQL client
│   │       │   └── parser.go       # Fitch response parser
│   │       └── factory.go          # Issuer client factory
│   ├── processor/
│   │   ├── rating_ingester.go      # Main ingestion logic
│   │   └── change_detector.go      # Hash-based change detection
│   └── shared/
│       ├── hash.go                 # SHA-256 content hash
│       ├── rate_limiter.go         # Token bucket rate limiter
│       ├── retry.go                # Retry with exponential backoff
│       └── name_normalizer.go      # Rating name normalization
├── Makefile
├── go.mod
└── go.sum
```

### 3.2 Domain Models

**File**: `internal/domain/models.go`

```go
package domain

import (
    "encoding/json"
    "time"

    "github.com/google/uuid"
)

// RatingIssuer represents a rating agency
type RatingIssuer struct {
    ID        uuid.UUID       `json:"id"`
    Code      string          `json:"code"`
    Name      string          `json:"name"`
    Website   string          `json:"website"`
    Config    IssuerConfig    `json:"config"`
    Enabled   bool            `json:"enabled"`
    CreatedAt time.Time       `json:"created_at"`
    UpdatedAt time.Time       `json:"updated_at"`
}

// IssuerConfig holds issuer-specific configuration
type IssuerConfig struct {
    APIURL           string `json:"api_url"`
    URLBase          string `json:"url_base"`
    ThrottleMs       int    `json:"throttle_ms"`
    GraphQLOperation string `json:"graphql_operation"`
    IdentifierField  string `json:"identifier_field"`
}

// InstitutionIssuerMapping maps an institution to an issuer with identifiers
type InstitutionIssuerMapping struct {
    ID                     uuid.UUID              `json:"id"`
    RatingIssuerID         uuid.UUID              `json:"rating_issuer_id"`
    FinancialInstitutionID uuid.UUID              `json:"financial_institution_id"`
    Config                 map[string]interface{} `json:"config"`
    Enabled                bool                   `json:"enabled"`
}

// Rating represents a financial institution rating
type Rating struct {
    ID                     uuid.UUID              `json:"id"`
    FinancialInstitutionID uuid.UUID              `json:"financial_institution_id"`
    IssuerID               uuid.UUID              `json:"issuer_id"`
    Name                   string                 `json:"name"`
    Label                  string                 `json:"label"`
    RatingDate             time.Time              `json:"rating_date"`
    CustomParams           map[string]interface{} `json:"custom_params"`
    SourceEvidenceID       *uuid.UUID             `json:"source_evidence_id"`
    ContentHash            string                 `json:"content_hash"`
    CreatedAt              time.Time              `json:"created_at"`
    UpdatedAt              time.Time              `json:"updated_at"`
}

// RatingHistory represents a historical rating record
type RatingHistory struct {
    ID                     uuid.UUID              `json:"id"`
    FinancialInstitutionID uuid.UUID              `json:"financial_institution_id"`
    IssuerID               uuid.UUID              `json:"issuer_id"`
    Name                   string                 `json:"name"`
    Label                  string                 `json:"label"`
    RatingDate             time.Time              `json:"rating_date"`
    CustomParams           map[string]interface{} `json:"custom_params"`
    SourceEvidenceID       *uuid.UUID             `json:"source_evidence_id"`
    ContentHash            string                 `json:"content_hash"`
    ChangedAt              time.Time              `json:"changed_at"`
}

// SourceEvidence stores the raw API response
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
    ContentType string `json:"content_type"`
    RequestID   string `json:"request_id,omitempty"`
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
    Rating     Rating
    ChangeType ChangeType
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
```

### 3.3 Domain Interfaces

**File**: `internal/domain/interfaces.go`

```go
package domain

import (
    "context"

    "github.com/google/uuid"
)

// IssuerClient fetches ratings from an external issuer API
type IssuerClient interface {
    // FetchRatings retrieves ratings for an institution
    FetchRatings(ctx context.Context, mapping InstitutionIssuerMapping) (*FetchResult, error)

    // GetIssuerCode returns the issuer code (e.g., "FITCH")
    GetIssuerCode() string
}

// IssuerClientFactory creates issuer-specific clients
type IssuerClientFactory interface {
    // CreateClient creates a client for the given issuer
    CreateClient(issuer RatingIssuer) (IssuerClient, error)
}

// RatingRepository manages rating persistence
type RatingRepository interface {
    // GetByInstitutionAndIssuer retrieves all ratings for an institution from an issuer
    GetByInstitutionAndIssuer(ctx context.Context, institutionID, issuerID uuid.UUID) ([]Rating, error)

    // GetByInstitutionIssuerAndName retrieves a specific rating
    GetByInstitutionIssuerAndName(ctx context.Context, institutionID, issuerID uuid.UUID, name string) (*Rating, error)

    // Upsert inserts or updates a rating
    Upsert(ctx context.Context, rating Rating) error

    // UpsertBatch inserts or updates multiple ratings in a transaction
    UpsertBatch(ctx context.Context, ratings []Rating) error
}

// HistoryRepository manages rating history persistence
type HistoryRepository interface {
    // Insert adds a history record
    Insert(ctx context.Context, history RatingHistory) error

    // InsertBatch adds multiple history records in a transaction
    InsertBatch(ctx context.Context, histories []RatingHistory) error

    // GetByInstitution retrieves all history for an institution
    GetByInstitution(ctx context.Context, institutionID uuid.UUID) ([]RatingHistory, error)
}

// EvidenceRepository manages evidence persistence
type EvidenceRepository interface {
    // Insert adds a new evidence record
    Insert(ctx context.Context, evidence SourceEvidence) error

    // GetByHash retrieves evidence by content hash
    GetByHash(ctx context.Context, hash string) (*SourceEvidence, error)

    // Exists checks if evidence with the given hash exists
    Exists(ctx context.Context, hash string) (bool, error)
}

// IssuerRepository manages issuer configuration
type IssuerRepository interface {
    // GetEnabled retrieves all enabled issuers
    GetEnabled(ctx context.Context) ([]RatingIssuer, error)

    // GetByCode retrieves an issuer by code
    GetByCode(ctx context.Context, code string) (*RatingIssuer, error)
}

// MappingRepository manages institution-to-issuer mappings
type MappingRepository interface {
    // GetEnabledByIssuer retrieves all enabled mappings for an issuer
    GetEnabledByIssuer(ctx context.Context, issuerID uuid.UUID) ([]InstitutionIssuerMapping, error)

    // GetByInstitutionAndIssuer retrieves a specific mapping
    GetByInstitutionAndIssuer(ctx context.Context, institutionID, issuerID uuid.UUID) (*InstitutionIssuerMapping, error)
}
```

### 3.4 Fitch Client Implementation

**File**: `internal/infrastructure/issuer/fitch/client.go`

```go
package fitch

import (
    "bytes"
    "context"
    "encoding/json"
    "fmt"
    "io"
    "net/http"
    "time"

    "github.com/google/uuid"
    "github.com/invistus/ratings/internal/domain"
    "github.com/invistus/ratings/internal/shared"
)

const (
    IssuerCode = "FITCH"

    graphQLQuery = `query Entity($slug: String!) {
        getEntity(slug: $slug) {
            slug
            name
            id
            ratings {
                id
                orangeDisplay
                correctionFlag
                ratingChangeDate
                ratingActionDescription
                ratingCode
                ratingAlertCode
                ratingEffectiveDate
                ratingTypeDescription
                ratingLocalValue
                ratingLocalActionDescription
                recoveryRatingValue
            }
        }
    }`
)

// Client implements the Fitch GraphQL API client
type Client struct {
    httpClient  *http.Client
    config      domain.IssuerConfig
    issuer      domain.RatingIssuer
    rateLimiter *shared.RateLimiter
}

// NewClient creates a new Fitch client
func NewClient(issuer domain.RatingIssuer) *Client {
    return &Client{
        httpClient: &http.Client{
            Timeout: 60 * time.Second,
        },
        config:      issuer.Config,
        issuer:      issuer,
        rateLimiter: shared.NewRateLimiter(time.Duration(issuer.Config.ThrottleMs) * time.Millisecond),
    }
}

// FetchRatings retrieves ratings for an institution from Fitch
func (c *Client) FetchRatings(ctx context.Context, mapping domain.InstitutionIssuerMapping) (*domain.FetchResult, error) {
    // Extract slug from mapping config
    slug, ok := mapping.Config["slug"].(string)
    if !ok || slug == "" {
        return nil, fmt.Errorf("missing or invalid slug in mapping config for institution %s", mapping.FinancialInstitutionID)
    }

    // Wait for rate limiter
    if err := c.rateLimiter.Wait(ctx); err != nil {
        return nil, fmt.Errorf("rate limiter error: %w", err)
    }

    // Build request
    reqBody := graphQLRequest{
        OperationName: "Entity",
        Variables:     map[string]string{"slug": slug},
        Query:         graphQLQuery,
    }

    bodyBytes, err := json.Marshal(reqBody)
    if err != nil {
        return nil, fmt.Errorf("failed to marshal request: %w", err)
    }

    req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.config.APIURL, bytes.NewReader(bodyBytes))
    if err != nil {
        return nil, fmt.Errorf("failed to create request: %w", err)
    }

    // Set headers
    req.Header.Set("Content-Type", "application/json")
    req.Header.Set("Accept", "*/*")
    req.Header.Set("Origin", "https://www.fitchratings.com")
    req.Header.Set("Referer", "https://www.fitchratings.com/")
    req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; InvistusBot/1.0)")

    // Execute request
    resp, err := c.httpClient.Do(req)
    if err != nil {
        return nil, fmt.Errorf("request failed: %w", err)
    }
    defer resp.Body.Close()

    // Read response body
    respBody, err := io.ReadAll(resp.Body)
    if err != nil {
        return nil, fmt.Errorf("failed to read response: %w", err)
    }

    // Create evidence record
    evidence := domain.SourceEvidence{
        ID:        uuid.New(),
        IssuerID:  c.issuer.ID,
        FetchedAt: time.Now().UTC(),
        Metadata: domain.EvidenceMetadata{
            URL:         c.config.APIURL,
            HTTPStatus:  resp.StatusCode,
            ContentType: resp.Header.Get("Content-Type"),
        },
        RawPayload:  string(respBody),
        ContentHash: shared.ComputeHash(respBody),
    }

    if resp.StatusCode != http.StatusOK {
        return &domain.FetchResult{
            InstitutionID: mapping.FinancialInstitutionID,
            IssuerID:      c.issuer.ID,
            Evidence:      evidence,
            Error:         fmt.Errorf("unexpected status code: %d", resp.StatusCode),
        }, nil
    }

    // Parse response
    ratings, err := parseResponse(respBody, mapping.FinancialInstitutionID, c.issuer.ID, &evidence.ID)
    if err != nil {
        return &domain.FetchResult{
            InstitutionID: mapping.FinancialInstitutionID,
            IssuerID:      c.issuer.ID,
            Evidence:      evidence,
            Error:         fmt.Errorf("failed to parse response: %w", err),
        }, nil
    }

    return &domain.FetchResult{
        InstitutionID: mapping.FinancialInstitutionID,
        IssuerID:      c.issuer.ID,
        Ratings:       ratings,
        Evidence:      evidence,
    }, nil
}

// GetIssuerCode returns the issuer code
func (c *Client) GetIssuerCode() string {
    return IssuerCode
}

// graphQLRequest represents the GraphQL request body
type graphQLRequest struct {
    OperationName string            `json:"operationName"`
    Variables     map[string]string `json:"variables"`
    Query         string            `json:"query"`
}
```

### 3.5 Fitch Response Parser

**File**: `internal/infrastructure/issuer/fitch/parser.go`

```go
package fitch

import (
    "encoding/json"
    "fmt"
    "time"

    "github.com/google/uuid"
    "github.com/invistus/ratings/internal/domain"
    "github.com/invistus/ratings/internal/shared"
)

// fitchResponse represents the GraphQL response structure
type fitchResponse struct {
    Data struct {
        GetEntity *fitchEntity `json:"getEntity"`
    } `json:"data"`
    Errors []struct {
        Message string `json:"message"`
    } `json:"errors,omitempty"`
}

type fitchEntity struct {
    Slug    string        `json:"slug"`
    Name    string        `json:"name"`
    ID      int64         `json:"id"`
    Ratings []fitchRating `json:"ratings"`
}

type fitchRating struct {
    ID                           string  `json:"id"`
    OrangeDisplay                bool    `json:"orangeDisplay"`
    CorrectionFlag               bool    `json:"correctionFlag"`
    RatingChangeDate             string  `json:"ratingChangeDate"`
    RatingActionDescription      string  `json:"ratingActionDescription"`
    RatingCode                   string  `json:"ratingCode"`
    RatingAlertCode              *string `json:"ratingAlertCode"`
    RatingEffectiveDate          string  `json:"ratingEffectiveDate"`
    RatingTypeDescription        string  `json:"ratingTypeDescription"`
    RatingLocalValue             string  `json:"ratingLocalValue"`
    RatingLocalActionDescription string  `json:"ratingLocalActionDescription"`
    RecoveryRatingValue          *string `json:"recoveryRatingValue"`
}

// parseResponse converts the Fitch API response to domain ratings
func parseResponse(body []byte, institutionID, issuerID uuid.UUID, evidenceID *uuid.UUID) ([]domain.Rating, error) {
    var resp fitchResponse
    if err := json.Unmarshal(body, &resp); err != nil {
        return nil, fmt.Errorf("failed to unmarshal response: %w", err)
    }

    if len(resp.Errors) > 0 {
        return nil, fmt.Errorf("GraphQL error: %s", resp.Errors[0].Message)
    }

    if resp.Data.GetEntity == nil {
        return nil, fmt.Errorf("entity not found")
    }

    ratings := make([]domain.Rating, 0, len(resp.Data.GetEntity.Ratings))

    for _, fr := range resp.Data.GetEntity.Ratings {
        // Skip withdrawn ratings
        if fr.RatingCode == "WD" {
            continue
        }

        // Parse rating date
        ratingDate, err := parseRatingDate(fr.RatingEffectiveDate)
        if err != nil {
            return nil, fmt.Errorf("failed to parse rating date for %s: %w", fr.RatingTypeDescription, err)
        }

        // Normalize rating name to UPPER_SNAKE_CASE
        name := shared.NormalizeRatingName(fr.RatingTypeDescription)

        // Build custom params
        customParams := map[string]interface{}{
            "fitch_id":       fr.ID,
            "action":         fr.RatingActionDescription,
            "local_value":    fr.RatingLocalValue,
            "local_action":   fr.RatingLocalActionDescription,
            "orange_display": fr.OrangeDisplay,
            "correction":     fr.CorrectionFlag,
        }

        if fr.RatingAlertCode != nil {
            customParams["alert_code"] = *fr.RatingAlertCode
        }
        if fr.RecoveryRatingValue != nil {
            customParams["recovery_value"] = *fr.RecoveryRatingValue
        }

        rating := domain.Rating{
            ID:                     uuid.New(),
            FinancialInstitutionID: institutionID,
            IssuerID:               issuerID,
            Name:                   name,
            Label:                  fr.RatingCode,
            RatingDate:             ratingDate,
            CustomParams:           customParams,
            SourceEvidenceID:       evidenceID,
        }

        // Compute content hash for idempotency
        rating.ContentHash = shared.ComputeRatingHash(rating)

        ratings = append(ratings, rating)
    }

    return ratings, nil
}

// parseRatingDate parses the Fitch date format
func parseRatingDate(dateStr string) (time.Time, error) {
    // Fitch uses ISO 8601 format: "2025-12-12T15:02:52.000Z"
    return time.Parse(time.RFC3339, dateStr)
}
```

### 3.6 Rating Ingester

**File**: `internal/processor/rating_ingester.go`

```go
package processor

import (
    "context"
    "fmt"
    "log/slog"
    "time"

    "github.com/invistus/ratings/internal/domain"
)

// RatingIngester processes ratings for all enabled institutions
type RatingIngester struct {
    issuerRepo   domain.IssuerRepository
    mappingRepo  domain.MappingRepository
    ratingRepo   domain.RatingRepository
    historyRepo  domain.HistoryRepository
    evidenceRepo domain.EvidenceRepository
    clientFactory domain.IssuerClientFactory
    logger       *slog.Logger
}

// NewRatingIngester creates a new ingester
func NewRatingIngester(
    issuerRepo domain.IssuerRepository,
    mappingRepo domain.MappingRepository,
    ratingRepo domain.RatingRepository,
    historyRepo domain.HistoryRepository,
    evidenceRepo domain.EvidenceRepository,
    clientFactory domain.IssuerClientFactory,
    logger *slog.Logger,
) *RatingIngester {
    return &RatingIngester{
        issuerRepo:    issuerRepo,
        mappingRepo:   mappingRepo,
        ratingRepo:    ratingRepo,
        historyRepo:   historyRepo,
        evidenceRepo:  evidenceRepo,
        clientFactory: clientFactory,
        logger:        logger,
    }
}

// ProcessAll processes ratings for all enabled issuers and institutions
func (ri *RatingIngester) ProcessAll(ctx context.Context) error {
    // Get enabled issuers
    issuers, err := ri.issuerRepo.GetEnabled(ctx)
    if err != nil {
        return fmt.Errorf("failed to get enabled issuers: %w", err)
    }

    ri.logger.Info("starting rating ingestion", "issuer_count", len(issuers))

    for _, issuer := range issuers {
        if err := ri.processIssuer(ctx, issuer); err != nil {
            ri.logger.Error("failed to process issuer",
                "issuer", issuer.Code,
                "error", err,
            )
            // Continue with other issuers
            continue
        }
    }

    ri.logger.Info("rating ingestion completed")
    return nil
}

// processIssuer processes all institutions for a single issuer
func (ri *RatingIngester) processIssuer(ctx context.Context, issuer domain.RatingIssuer) error {
    ri.logger.Info("processing issuer", "issuer", issuer.Code)

    // Create client for this issuer
    client, err := ri.clientFactory.CreateClient(issuer)
    if err != nil {
        return fmt.Errorf("failed to create client: %w", err)
    }

    // Get enabled mappings for this issuer
    mappings, err := ri.mappingRepo.GetEnabledByIssuer(ctx, issuer.ID)
    if err != nil {
        return fmt.Errorf("failed to get mappings: %w", err)
    }

    ri.logger.Info("found institution mappings",
        "issuer", issuer.Code,
        "count", len(mappings),
    )

    for _, mapping := range mappings {
        if err := ri.processInstitution(ctx, client, mapping); err != nil {
            ri.logger.Error("failed to process institution",
                "issuer", issuer.Code,
                "institution_id", mapping.FinancialInstitutionID,
                "error", err,
            )
            // Continue with other institutions
            continue
        }
    }

    return nil
}

// processInstitution fetches and upserts ratings for a single institution
func (ri *RatingIngester) processInstitution(ctx context.Context, client domain.IssuerClient, mapping domain.InstitutionIssuerMapping) error {
    ri.logger.Info("fetching ratings",
        "issuer", client.GetIssuerCode(),
        "institution_id", mapping.FinancialInstitutionID,
        "config", mapping.Config,
    )

    // Fetch ratings from issuer API
    result, err := client.FetchRatings(ctx, mapping)
    if err != nil {
        return fmt.Errorf("failed to fetch ratings: %w", err)
    }

    // Always store evidence (even on error)
    if err := ri.storeEvidence(ctx, result.Evidence); err != nil {
        ri.logger.Warn("failed to store evidence",
            "error", err,
        )
    }

    if result.Error != nil {
        return fmt.Errorf("fetch error: %w", result.Error)
    }

    // Process each rating
    changes := ri.detectChanges(ctx, result.Ratings, mapping.FinancialInstitutionID, mapping.RatingIssuerID)

    // Upsert ratings and create history for changes
    for _, change := range changes {
        if change.ChangeType == domain.ChangeTypeUnchanged {
            continue
        }

        // Upsert the rating
        if err := ri.ratingRepo.Upsert(ctx, change.Rating); err != nil {
            ri.logger.Error("failed to upsert rating",
                "name", change.Rating.Name,
                "error", err,
            )
            continue
        }

        // Insert history record for changes
        history := domain.RatingHistory{
            FinancialInstitutionID: change.Rating.FinancialInstitutionID,
            IssuerID:               change.Rating.IssuerID,
            Name:                   change.Rating.Name,
            Label:                  change.Rating.Label,
            RatingDate:             change.Rating.RatingDate,
            CustomParams:           change.Rating.CustomParams,
            SourceEvidenceID:       change.Rating.SourceEvidenceID,
            ContentHash:            change.Rating.ContentHash,
            ChangedAt:              time.Now().UTC(),
        }

        if err := ri.historyRepo.Insert(ctx, history); err != nil {
            ri.logger.Error("failed to insert history",
                "name", change.Rating.Name,
                "error", err,
            )
        }

        ri.logger.Info("rating processed",
            "name", change.Rating.Name,
            "label", change.Rating.Label,
            "change_type", change.ChangeType,
        )
    }

    return nil
}

// detectChanges compares fetched ratings with existing records
func (ri *RatingIngester) detectChanges(ctx context.Context, ratings []domain.Rating, institutionID, issuerID domain.UUID) []domain.RatingChange {
    changes := make([]domain.RatingChange, 0, len(ratings))

    for _, rating := range ratings {
        existing, err := ri.ratingRepo.GetByInstitutionIssuerAndName(ctx, institutionID, issuerID, rating.Name)

        var change domain.RatingChange
        change.Rating = rating

        if err != nil || existing == nil {
            change.ChangeType = domain.ChangeTypeCreated
        } else if existing.ContentHash == rating.ContentHash {
            change.ChangeType = domain.ChangeTypeUnchanged
            change.PreviousHash = existing.ContentHash
        } else {
            change.ChangeType = domain.ChangeTypeUpdated
            change.PreviousHash = existing.ContentHash
            // Preserve the existing ID for update
            change.Rating.ID = existing.ID
        }

        changes = append(changes, change)
    }

    return changes
}

// storeEvidence saves the evidence record if it doesn't exist
func (ri *RatingIngester) storeEvidence(ctx context.Context, evidence domain.SourceEvidence) error {
    // Check if evidence with this hash already exists
    exists, err := ri.evidenceRepo.Exists(ctx, evidence.ContentHash)
    if err != nil {
        return err
    }

    if exists {
        ri.logger.Debug("evidence already exists", "hash", evidence.ContentHash[:16])
        return nil
    }

    return ri.evidenceRepo.Insert(ctx, evidence)
}
```

### 3.7 Shared Utilities

**File**: `internal/shared/hash.go`

```go
package shared

import (
    "crypto/sha256"
    "encoding/hex"
    "encoding/json"
    "fmt"
    "sort"
    "strings"

    "github.com/invistus/ratings/internal/domain"
)

// ComputeHash computes SHA-256 hash of raw bytes
func ComputeHash(data []byte) string {
    hash := sha256.Sum256(data)
    return hex.EncodeToString(hash[:])
}

// ComputeRatingHash computes a content hash for idempotency detection
// Hash is based on: issuer_id + name + label + rating_date + normalized_custom_params
func ComputeRatingHash(rating domain.Rating) string {
    // Normalize custom params by sorting keys
    normalizedParams := normalizeJSON(rating.CustomParams)

    // Build canonical string
    canonical := fmt.Sprintf("%s|%s|%s|%s|%s",
        rating.IssuerID.String(),
        rating.Name,
        strings.ToUpper(rating.Label),
        rating.RatingDate.Format("2006-01-02"),
        normalizedParams,
    )

    return ComputeHash([]byte(canonical))
}

// normalizeJSON returns a deterministic JSON string with sorted keys
func normalizeJSON(m map[string]interface{}) string {
    if m == nil {
        return "{}"
    }

    // Get sorted keys
    keys := make([]string, 0, len(m))
    for k := range m {
        keys = append(keys, k)
    }
    sort.Strings(keys)

    // Build normalized JSON
    var sb strings.Builder
    sb.WriteString("{")
    for i, k := range keys {
        if i > 0 {
            sb.WriteString(",")
        }
        v, _ := json.Marshal(m[k])
        sb.WriteString(fmt.Sprintf("%q:%s", k, string(v)))
    }
    sb.WriteString("}")

    return sb.String()
}
```

**File**: `internal/shared/name_normalizer.go`

```go
package shared

import (
    "regexp"
    "strings"
)

var (
    nonAlphaNumeric = regexp.MustCompile(`[^a-zA-Z0-9]+`)
    multipleUnderscores = regexp.MustCompile(`_+`)
)

// NormalizeRatingName converts a rating type description to UPPER_SNAKE_CASE
// Example: "Long Term Issuer Default Rating" -> "LONG_TERM_ISSUER_DEFAULT_RATING"
func NormalizeRatingName(name string) string {
    // Replace non-alphanumeric characters with underscores
    normalized := nonAlphaNumeric.ReplaceAllString(name, "_")

    // Collapse multiple underscores
    normalized = multipleUnderscores.ReplaceAllString(normalized, "_")

    // Trim leading/trailing underscores and convert to uppercase
    normalized = strings.Trim(normalized, "_")

    return strings.ToUpper(normalized)
}
```

**File**: `internal/shared/rate_limiter.go`

```go
package shared

import (
    "context"
    "sync"
    "time"
)

// RateLimiter implements a simple token bucket rate limiter
type RateLimiter struct {
    interval time.Duration
    lastCall time.Time
    mu       sync.Mutex
}

// NewRateLimiter creates a new rate limiter with the specified interval
func NewRateLimiter(interval time.Duration) *RateLimiter {
    return &RateLimiter{
        interval: interval,
    }
}

// Wait blocks until the rate limit allows the next request
func (rl *RateLimiter) Wait(ctx context.Context) error {
    rl.mu.Lock()
    defer rl.mu.Unlock()

    now := time.Now()
    elapsed := now.Sub(rl.lastCall)

    if elapsed < rl.interval {
        waitDuration := rl.interval - elapsed

        select {
        case <-ctx.Done():
            return ctx.Err()
        case <-time.After(waitDuration):
        }
    }

    rl.lastCall = time.Now()
    return nil
}
```

### 3.8 Main Entry Point

**File**: `cmd/cronjob/main.go`

```go
package main

import (
    "context"
    "log/slog"
    "os"
    "os/signal"
    "syscall"

    "github.com/invistus/ratings/internal/application"
    "github.com/invistus/ratings/internal/infrastructure/database"
    "github.com/invistus/ratings/internal/infrastructure/issuer"
    "github.com/invistus/ratings/internal/processor"
)

func main() {
    // Setup structured logging
    logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
        Level: slog.LevelInfo,
    }))
    slog.SetDefault(logger)

    logger.Info("starting ratings cronjob")

    // Load configuration
    cfg, err := application.LoadConfig()
    if err != nil {
        logger.Error("failed to load config", "error", err)
        os.Exit(1)
    }

    // Setup context with cancellation
    ctx, cancel := context.WithCancel(context.Background())
    defer cancel()

    // Handle graceful shutdown
    sigCh := make(chan os.Signal, 1)
    signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
    go func() {
        sig := <-sigCh
        logger.Info("received signal, shutting down", "signal", sig)
        cancel()
    }()

    // Initialize database connection
    db, err := database.NewPostgresPool(ctx, cfg.Database)
    if err != nil {
        logger.Error("failed to connect to database", "error", err)
        os.Exit(1)
    }
    defer db.Close()

    // Initialize repositories
    issuerRepo := database.NewIssuerRepository(db)
    mappingRepo := database.NewMappingRepository(db)
    ratingRepo := database.NewRatingRepository(db)
    historyRepo := database.NewHistoryRepository(db)
    evidenceRepo := database.NewEvidenceRepository(db)

    // Initialize issuer client factory
    clientFactory := issuer.NewFactory()

    // Initialize ingester
    ingester := processor.NewRatingIngester(
        issuerRepo,
        mappingRepo,
        ratingRepo,
        historyRepo,
        evidenceRepo,
        clientFactory,
        logger,
    )

    // Run ingestion
    if err := ingester.ProcessAll(ctx); err != nil {
        logger.Error("ingestion failed", "error", err)
        os.Exit(1)
    }

    logger.Info("ratings cronjob completed successfully")
}
```

---

## 4. Kotlin API Extension

### 4.1 New Entities

**File**: `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/model/RatingEntities.kt`

```kotlin
package io.github.salomax.neotool.financialdata.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@MappedEntity(value = "rating_issuer", schema = "financial_data")
data class RatingIssuerEntity(
    @field:Id
    val id: UUID,
    val code: String,
    val name: String,
    val website: String?,
    val config: String, // JSONB stored as String
    val logoAssetId: UUID?,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

@MappedEntity(value = "financial_institution_rating", schema = "financial_data")
data class FinancialInstitutionRatingEntity(
    @field:Id
    val id: UUID,
    val financialInstitutionId: UUID,
    val issuerId: UUID,
    val name: String,
    val label: String,
    val ratingDate: LocalDate,
    val customParams: String, // JSONB stored as String
    val sourceEvidenceId: UUID?,
    val contentHash: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

@MappedEntity(value = "financial_institution_rating_history", schema = "financial_data")
data class FinancialInstitutionRatingHistoryEntity(
    @field:Id
    val id: UUID,
    val financialInstitutionId: UUID,
    val issuerId: UUID,
    val name: String,
    val label: String,
    val ratingDate: LocalDate,
    val customParams: String,
    val sourceEvidenceId: UUID?,
    val contentHash: String,
    val changedAt: OffsetDateTime
)
```

### 4.2 New Repositories

**File**: `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/repo/RatingRepositories.kt`

```kotlin
package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.FinancialInstitutionRatingEntity
import io.github.salomax.neotool.financialdata.model.FinancialInstitutionRatingHistoryEntity
import io.github.salomax.neotool.financialdata.model.RatingIssuerEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.util.UUID

@JdbcRepository(dialect = Dialect.POSTGRES)
interface RatingIssuerRepository : CrudRepository<RatingIssuerEntity, UUID> {
    fun findByCode(code: String): RatingIssuerEntity?
    fun findByEnabledTrue(): List<RatingIssuerEntity>
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface FinancialInstitutionRatingRepository : CrudRepository<FinancialInstitutionRatingEntity, UUID> {
    fun findByFinancialInstitutionId(financialInstitutionId: UUID): List<FinancialInstitutionRatingEntity>
    fun findByFinancialInstitutionIdAndIssuerId(
        financialInstitutionId: UUID,
        issuerId: UUID
    ): List<FinancialInstitutionRatingEntity>
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface FinancialInstitutionRatingHistoryRepository : CrudRepository<FinancialInstitutionRatingHistoryEntity, UUID> {
    fun findByFinancialInstitutionIdOrderByChangedAtDesc(
        financialInstitutionId: UUID
    ): List<FinancialInstitutionRatingHistoryEntity>
}
```

### 4.3 DTOs

**File**: `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/graphql/dto/RatingDTOs.kt`

```kotlin
package io.github.salomax.neotool.financialdata.graphql.dto

import java.time.LocalDate
import java.time.OffsetDateTime

data class RatingIssuerDTO(
    val id: String,
    val code: String,
    val name: String,
    val website: String?,
    val logoAssetId: String?
)

data class InstitutionRatingDTO(
    val id: String,
    val name: String,
    val label: String,
    val ratingDate: LocalDate,
    val customParams: Map<String, Any>?,
    val issuer: RatingIssuerDTO,
    val updatedAt: OffsetDateTime
)

data class RatingHistoryDTO(
    val id: String,
    val name: String,
    val label: String,
    val ratingDate: LocalDate,
    val customParams: Map<String, Any>?,
    val issuerCode: String,
    val changedAt: OffsetDateTime
)
```

### 4.4 Extend BacenFinanceInstitutionDTO

**File**: Update `InstitutionDetailDTOs.kt`

Add the following field to `BacenFinanceInstitutionDTO`:

```kotlin
data class BacenFinanceInstitutionDTO(
    // ... existing fields ...

    // New field for ratings
    val ratings: List<InstitutionRatingDTO>?
)
```

### 4.5 GraphQL Schema Extension

**File**: `service/kotlin/financialdata/src/main/resources/schema/financial-institution-ratings.graphqls`

```graphql
type RatingIssuer {
    id: ID!
    code: String!
    name: String!
    website: String
    logoAssetId: String
}

type InstitutionRating {
    id: ID!
    name: String!
    label: String!
    ratingDate: Date!
    customParams: JSON
    issuer: RatingIssuer!
    updatedAt: DateTime!
}

type RatingHistory {
    id: ID!
    name: String!
    label: String!
    ratingDate: Date!
    customParams: JSON
    issuerCode: String!
    changedAt: DateTime!
}

extend type BacenFinanceInstitution {
    """
    Current ratings from all issuers for this institution
    """
    ratings: [InstitutionRating!]

    """
    Historical rating changes for this institution
    """
    ratingHistory(limit: Int = 50): [RatingHistory!]
}

extend type Query {
    """
    Get all enabled rating issuers
    """
    ratingIssuers: [RatingIssuer!]!
}
```

---

## 5. Kubernetes Deployment

### 5.1 CronJob Manifest

**File**: `k8s/cronjobs/financial-data-ratings.yaml`

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: financial-data-ratings
  namespace: financial-data
  labels:
    app: financial-data-ratings
    component: cronjob
spec:
  schedule: "0 6 * * 0"  # Weekly on Sunday at 6 AM UTC
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      backoffLimit: 2
      activeDeadlineSeconds: 3600  # 1 hour max
      template:
        metadata:
          labels:
            app: financial-data-ratings
        spec:
          restartPolicy: OnFailure
          containers:
            - name: ratings-cronjob
              image: ghcr.io/invistus/financial-data-ratings:latest
              imagePullPolicy: Always
              resources:
                requests:
                  memory: "128Mi"
                  cpu: "100m"
                limits:
                  memory: "512Mi"
                  cpu: "500m"
              envFrom:
                - secretRef:
                    name: financial-data-ratings-secrets
                - configMapRef:
                    name: financial-data-ratings-config
              livenessProbe:
                exec:
                  command:
                    - /bin/sh
                    - -c
                    - "pgrep -f ratings-cronjob || exit 1"
                initialDelaySeconds: 10
                periodSeconds: 30
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: financial-data-ratings-config
  namespace: financial-data
data:
  LOG_LEVEL: "info"
  LOG_FORMAT: "json"
  POSTGRES_SSL_MODE: "require"
  POSTGRES_POOL_MIN: "2"
  POSTGRES_POOL_MAX: "5"
---
apiVersion: v1
kind: Secret
metadata:
  name: financial-data-ratings-secrets
  namespace: financial-data
type: Opaque
stringData:
  POSTGRES_HOST: "postgres.financial-data.svc.cluster.local"
  POSTGRES_PORT: "5432"
  POSTGRES_DB: "invistus"
  POSTGRES_USER: "ratings_worker"
  POSTGRES_PASSWORD: "${POSTGRES_PASSWORD}"
```

---

## 6. Configuration

### 6.1 Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `POSTGRES_HOST` | PostgreSQL host | - | Yes |
| `POSTGRES_PORT` | PostgreSQL port | `5432` | No |
| `POSTGRES_DB` | Database name | - | Yes |
| `POSTGRES_USER` | Database user | - | Yes |
| `POSTGRES_PASSWORD` | Database password | - | Yes |
| `POSTGRES_SSL_MODE` | SSL mode | `disable` | No |
| `POSTGRES_POOL_MIN` | Min pool connections | `2` | No |
| `POSTGRES_POOL_MAX` | Max pool connections | `10` | No |
| `LOG_LEVEL` | Logging level | `info` | No |
| `LOG_FORMAT` | Log format (json/text) | `json` | No |

---

## 7. Testing Strategy

### 7.1 Unit Tests

- Domain model validation
- Hash computation consistency
- Name normalization (UPPER_SNAKE_CASE)
- Fitch response parsing
- Change detection logic

### 7.2 Integration Tests

- Database repository operations (CRUD)
- Idempotency verification (same data = no changes)
- History record creation on changes
- Evidence deduplication

### 7.3 E2E Tests

- Full ingestion flow with mock Fitch API
- Rate limiting behavior
- Error handling and recovery

---

## 8. Implementation Tasks

### Phase 1: Database & Infrastructure

1. [ ] Create database migration `V1_6__create_rating_tables.sql`
2. [ ] Create seed data migration `V1_7__seed_rating_issuers.sql`
3. [ ] Run migrations and verify schema

### Phase 2: Go Workflow

4. [ ] Initialize Go module with directory structure
5. [ ] Implement domain models and interfaces
6. [ ] Implement shared utilities (hash, rate limiter, name normalizer)
7. [ ] Implement database repositories
8. [ ] Implement Fitch client and parser
9. [ ] Implement rating ingester processor
10. [ ] Implement cronjob main entry point
11. [ ] Write unit tests
12. [ ] Write integration tests

### Phase 3: Kotlin API

13. [ ] Create Kotlin entities for rating tables
14. [ ] Create repositories
15. [ ] Create DTOs
16. [ ] Extend `BacenFinanceInstitutionDTO` with ratings
17. [ ] Add GraphQL schema extension
18. [ ] Implement resolvers
19. [ ] Write API tests

### Phase 4: Deployment

20. [ ] Create Kubernetes manifests
21. [ ] Configure secrets and config maps
22. [ ] Deploy to staging
23. [ ] Seed initial institution-to-issuer mappings
24. [ ] Run manual test execution
25. [ ] Deploy to production

---

## 9. Monitoring & Observability

### 9.1 Metrics

- `ratings_ingestion_total` - Counter of ingestion runs
- `ratings_fetched_total` - Counter of ratings fetched per issuer
- `ratings_changed_total` - Counter of rating changes (created/updated)
- `ratings_fetch_duration_seconds` - Histogram of fetch duration
- `ratings_fetch_errors_total` - Counter of fetch errors per issuer

### 9.2 Alerts

- Cronjob failure (no successful run in 2 weeks)
- High error rate (>10% fetch failures)
- Evidence storage growth (>1GB/month)

### 9.3 Logs

Structured JSON logs with fields:
- `issuer`: Issuer code
- `institution_id`: Financial institution UUID
- `rating_name`: Rating type name
- `change_type`: CREATED/UPDATED/UNCHANGED
- `duration_ms`: Operation duration

---

## 10. Security Considerations

- Database credentials stored in Kubernetes secrets
- No API tokens stored in code (use environment variables)
- Evidence payloads may contain sensitive data (access restricted)
- Rate limiting prevents API abuse
- Input validation on all external data

---

## 11. Future Considerations

### Phase 2: S&P Global Integration

- Different API structure (REST vs GraphQL)
- Different identifier scheme (entity_id)
- May require authentication token

### Phase 3: Moody's Integration

- Potentially web scraping instead of API
- Different rate limiting requirements
- HTML evidence storage

### Scalability

- If institution count grows significantly, consider:
  - Parallel processing with worker pool
  - Sharding by issuer
  - Kafka-based event-driven architecture
