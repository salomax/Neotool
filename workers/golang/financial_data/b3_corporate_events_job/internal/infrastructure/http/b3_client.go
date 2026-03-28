package http

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/b3-corporate-events/internal/shared"
)

const (
	pathListCompanies   = "/listedCompaniesProxy/CompanyCall/GetInitialCompanies/"
	pathSupplementData  = "/listedCompaniesProxy/CompanyCall/GetListedSupplementCompany/"
)

// B3Client fetches corporate events data from B3's public API.
type B3Client struct {
	baseURL    string
	httpClient *http.Client
	retry      shared.RetryPolicy
	logger     *slog.Logger
}

func NewB3Client(baseURL string, timeoutSec int, logger *slog.Logger) *B3Client {
	return &B3Client{
		baseURL:    baseURL,
		httpClient: &http.Client{Timeout: time.Duration(timeoutSec) * time.Second},
		retry:      shared.DefaultRetryPolicy,
		logger:     logger,
	}
}

// GetListedCompanies fetches one page of listed companies.
// Returns the response including pagination metadata.
func (c *B3Client) GetListedCompanies(ctx context.Context, pageNumber, pageSize int) (*domain.B3CompanyListResponse, error) {
	payload := map[string]any{
		"language":   "pt-br",
		"pageNumber": pageNumber,
		"pageSize":   pageSize,
	}

	var result domain.B3CompanyListResponse
	if err := c.fetchJSON(ctx, pathListCompanies, payload, &result); err != nil {
		return nil, fmt.Errorf("GetListedCompanies page=%d: %w", pageNumber, err)
	}
	return &result, nil
}

// GetCompanyEvents fetches all corporate events (dividends, splits, subscriptions)
// for a single company identified by codeCVM and issuingCompany (ticker prefix).
func (c *B3Client) GetCompanyEvents(ctx context.Context, codeCVM, issuingCompany string) ([]domain.B3SupplementItem, error) {
	payload := map[string]any{
		"language":       "pt-br",
		"codeCVM":        codeCVM,
		"issuingCompany": issuingCompany,
	}

	var result []domain.B3SupplementItem
	if err := c.fetchJSON(ctx, pathSupplementData, payload, &result); err != nil {
		return nil, fmt.Errorf("GetCompanyEvents codeCVM=%s: %w", codeCVM, err)
	}
	return result, nil
}

// fetchJSON encodes payload as base64 JSON, GETs the endpoint, and decodes into dest.
func (c *B3Client) fetchJSON(ctx context.Context, path string, payload map[string]any, dest any) error {
	encodedPayload, err := encodePayload(payload)
	if err != nil {
		return err
	}

	url := c.baseURL + path + encodedPayload

	return shared.Retry(ctx, c.retry, func(ctx context.Context) error {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
		if err != nil {
			return err
		}
		req.Header.Set("Accept", "application/json")
		req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; invistus/1.0)")

		resp, err := c.httpClient.Do(req)
		if err != nil {
			return fmt.Errorf("GET %s: %w", path, err)
		}
		defer resp.Body.Close()

		if resp.StatusCode == http.StatusTooManyRequests {
			return fmt.Errorf("GET %s: rate limited (429)", path)
		}
		if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("GET %s: unexpected status %d", path, resp.StatusCode)
		}

		if err := json.NewDecoder(resp.Body).Decode(dest); err != nil {
			return fmt.Errorf("decoding response from %s: %w", path, err)
		}
		return nil
	}, isRetryable, c.logger)
}

// encodePayload serialises the payload map to JSON and base64-encodes it.
// B3's API uses the base64-encoded JSON as a path segment.
func encodePayload(payload map[string]any) (string, error) {
	b, err := json.Marshal(payload)
	if err != nil {
		return "", fmt.Errorf("marshalling payload: %w", err)
	}
	return base64.StdEncoding.EncodeToString(b), nil
}

func isRetryable(err error) bool {
	return !errors.Is(err, context.Canceled) && !errors.Is(err, context.DeadlineExceeded)
}
