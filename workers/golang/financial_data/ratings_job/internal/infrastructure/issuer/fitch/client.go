package fitch

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/invistus/ratings/internal/domain"
	"github.com/invistus/ratings/internal/shared"
)

const (
	// IssuerCode is the unique code for Fitch
	IssuerCode = "FITCH"

	// GraphQL query for fetching entity ratings
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

	suggestQuery = `query Suggest($item: SearchItem, $limit: Int, $term: String!, $filter: SearchFilterInput) {
  suggest(item: $item, limit: $limit, term: $term, filter: $filter) {
    entity {
      name
      permalink
      ultimateParent
    }
  }
}`

	entityNameQuery = `query Entity($slug: String!) {
  getEntity(slug: $slug) {
    name
  }
}`
)

// Client implements the Fitch GraphQL API client
type Client struct {
	httpClient  *http.Client
	apiURL      string
	issuer      domain.RatingIssuer
	rateLimiter *shared.RateLimiter
	retryPolicy shared.RetryPolicy
	logger      *slog.Logger
}

// NewClient creates a new Fitch client
func NewClient(issuer domain.RatingIssuer, timeout, throttle time.Duration, retryPolicy shared.RetryPolicy, logger *slog.Logger) *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: timeout,
		},
		apiURL:      issuer.Config.APIURL,
		issuer:      issuer,
		rateLimiter: shared.NewRateLimiter(throttle),
		retryPolicy: retryPolicy,
		logger:      logger.With("issuer", IssuerCode),
	}
}

// FetchRatings retrieves ratings for an institution from Fitch
func (c *Client) FetchRatings(ctx context.Context, mapping domain.InstitutionIssuerMapping) (*domain.FetchResult, error) {
	// Extract slug from mapping config
	slug := mapping.Config.Slug
	if slug == "" {
		return nil, domain.ErrInvalidSlug
	}

	c.logger.Info("fetching ratings",
		"institution_id", mapping.FinancialInstitutionID,
		"slug", slug,
	)

	// Wait for rate limiter
	if err := c.rateLimiter.Wait(ctx); err != nil {
		return nil, fmt.Errorf("rate limiter error: %w", err)
	}

	// Execute request with retry
	result, err := shared.Retry(ctx, c.retryPolicy, func(ctx context.Context) (*domain.FetchResult, error) {
		return c.doFetch(ctx, mapping, slug)
	}, isRetryableError, c.logger)

	if err != nil {
		return nil, err
	}

	return result, nil
}

// SuggestSlug returns the first suggested permalink for a legal name.
func (c *Client) SuggestSlug(ctx context.Context, term string) (string, error) {
	if term == "" {
		return "", nil
	}

	if err := c.rateLimiter.Wait(ctx); err != nil {
		return "", fmt.Errorf("rate limiter error: %w", err)
	}

	respBody, err := shared.Retry(ctx, c.retryPolicy, func(ctx context.Context) ([]byte, error) {
		return c.doGraphQLRequest(ctx, "Suggest", suggestQuery, map[string]interface{}{
			"item":  "ENTITY",
			"term":  term,
			"limit": 1,
		})
	}, isRetryableError, c.logger)
	if err != nil {
		return "", err
	}

	var resp suggestResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return "", fmt.Errorf("failed to parse suggest response: %w", err)
	}

	if len(resp.Data.Suggest.Entity) == 0 {
		return "", nil
	}

	return resp.Data.Suggest.Entity[0].Permalink, nil
}

// FetchEntityName retrieves the entity name for a slug.
func (c *Client) FetchEntityName(ctx context.Context, slug string) (string, error) {
	if slug == "" {
		return "", domain.ErrInvalidSlug
	}

	if err := c.rateLimiter.Wait(ctx); err != nil {
		return "", fmt.Errorf("rate limiter error: %w", err)
	}

	respBody, err := shared.Retry(ctx, c.retryPolicy, func(ctx context.Context) ([]byte, error) {
		return c.doGraphQLRequest(ctx, "Entity", entityNameQuery, map[string]interface{}{
			"slug": slug,
		})
	}, isRetryableError, c.logger)
	if err != nil {
		return "", err
	}

	var resp entityNameResponse
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return "", fmt.Errorf("failed to parse entity response: %w", err)
	}

	if resp.Data.GetEntity == nil {
		return "", domain.ErrEntityNotFound
	}

	return resp.Data.GetEntity.Name, nil
}

// GetIssuerCode returns the issuer code
func (c *Client) GetIssuerCode() string {
	return IssuerCode
}

// doFetch performs the actual HTTP request
func (c *Client) doFetch(ctx context.Context, mapping domain.InstitutionIssuerMapping, slug string) (*domain.FetchResult, error) {
	// Build request
	reqBody := graphQLRequest{
		OperationName: "Entity",
		Variables:     map[string]interface{}{"slug": slug},
		Query:         graphQLQuery,
	}

	bodyBytes, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.apiURL, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Set headers (mimicking browser request)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
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
			URL:         c.apiURL,
			HTTPStatus:  resp.StatusCode,
			ContentType: resp.Header.Get("Content-Type"),
			Slug:        slug,
		},
		RawPayload:  string(respBody),
		ContentHash: shared.ComputeHash(respBody),
	}

	// Retry on rate limiting or transient server errors.
	if resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode >= http.StatusInternalServerError {
		return nil, domain.FetchError{
			Err:      fmt.Errorf("retryable status code: %d", resp.StatusCode),
			Evidence: &evidence,
		}
	}

	// Check for HTTP errors
	if resp.StatusCode != http.StatusOK {
		return &domain.FetchResult{
			InstitutionID: mapping.FinancialInstitutionID,
			IssuerID:      c.issuer.ID,
			Evidence:      evidence,
			Error:         fmt.Errorf("unexpected status code: %d", resp.StatusCode),
		}, nil
	}

	// Parse response
	ratings, err := parseResponse(respBody, mapping.FinancialInstitutionID, c.issuer.ID, &evidence.ID, c.logger)
	if err != nil {
		return &domain.FetchResult{
			InstitutionID: mapping.FinancialInstitutionID,
			IssuerID:      c.issuer.ID,
			Evidence:      evidence,
			Error:         fmt.Errorf("failed to parse response: %w", err),
		}, nil
	}

	c.logger.Info("fetched ratings successfully",
		"institution_id", mapping.FinancialInstitutionID,
		"slug", slug,
		"rating_count", len(ratings),
	)

	return &domain.FetchResult{
		InstitutionID: mapping.FinancialInstitutionID,
		IssuerID:      c.issuer.ID,
		Ratings:       ratings,
		Evidence:      evidence,
	}, nil
}

// graphQLRequest represents the GraphQL request body
type graphQLRequest struct {
	OperationName string                 `json:"operationName"`
	Variables     map[string]interface{} `json:"variables"`
	Query         string                 `json:"query"`
}

// isRetryableError determines if an error should be retried
func isRetryableError(err error) bool {
	if err == nil {
		return false
	}

	// Network errors are generally retryable
	// HTTP 5xx errors are retryable
	// HTTP 429 (rate limit) is retryable

	// For now, retry all errors except context cancellation
	if err == context.Canceled || err == context.DeadlineExceeded {
		return false
	}

	return true
}

func (c *Client) doGraphQLRequest(ctx context.Context, operationName, query string, variables map[string]interface{}) ([]byte, error) {
	reqBody := graphQLRequest{
		OperationName: operationName,
		Variables:     variables,
		Query:         query,
	}

	bodyBytes, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.apiURL, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "*/*")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	req.Header.Set("Origin", "https://www.fitchratings.com")
	req.Header.Set("Referer", "https://www.fitchratings.com/")
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; InvistusBot/1.0)")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode >= http.StatusInternalServerError {
		return nil, fmt.Errorf("retryable status code: %d", resp.StatusCode)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	return respBody, nil
}

type suggestResponse struct {
	Data struct {
		Suggest struct {
			Entity []struct {
				Name           string `json:"name"`
				Permalink      string `json:"permalink"`
				UltimateParent bool   `json:"ultimateParent"`
			} `json:"entity"`
		} `json:"suggest"`
	} `json:"data"`
}

type entityNameResponse struct {
	Data struct {
		GetEntity *struct {
			Name string `json:"name"`
		} `json:"getEntity"`
	} `json:"data"`
}
