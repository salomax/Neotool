package http

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/shared"
)

type searchCandidateDTO struct {
	Name    string `json:"name"`
	Domain  string `json:"domain"`
	LogoURL string `json:"logoUrl"`
}

type Client struct {
	baseURL      string
	imageBaseURL string
	token        string
	httpClient   *http.Client
	logger       *slog.Logger
}

func NewClient(baseURL string, imageBaseURL string, token string, timeoutSeconds int, logger *slog.Logger) *Client {
	return &Client{
		baseURL:      strings.TrimRight(baseURL, "/"),
		imageBaseURL: strings.TrimRight(imageBaseURL, "/"),
		token:        token,
		httpClient: &http.Client{
			Timeout: time.Duration(timeoutSeconds) * time.Second,
		},
		logger: logger,
	}
}

func (c *Client) Search(ctx context.Context, query string) (domain.SearchResponse, error) {
	var response domain.SearchResponse

	err := shared.Retry(
		ctx,
		shared.DefaultRetryPolicy,
		func(ctx context.Context) error {
			escapedQuery := url.QueryEscape(query)
			req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"/search?q="+escapedQuery, nil)
			if err != nil {
				return err
			}
			req.Header.Set("Authorization", "Bearer "+c.token)

			httpResp, err := c.httpClient.Do(req)
			if err != nil {
				return err
			}
			defer httpResp.Body.Close()

			if httpResp.StatusCode < 200 || httpResp.StatusCode > 299 {
				return &retryableHTTPError{StatusCode: httpResp.StatusCode}
			}

			var payload []searchCandidateDTO
			raw, err := shared.ReadAllTrimmed(httpResp.Body)
			if err != nil {
				return fmt.Errorf("read body: %w", err)
			}
			response.RawPayload = raw
			if raw == "" {
				response.Results = nil
				return nil
			}
			if err := json.Unmarshal([]byte(raw), &payload); err != nil {
				return fmt.Errorf("decode body: %w", err)
			}

			response.Results = make([]domain.SearchCandidate, 0, len(payload))
			for _, item := range payload {
				response.Results = append(response.Results, domain.SearchCandidate{
					Name:    item.Name,
					Domain:  item.Domain,
					LogoURL: item.LogoURL,
				})
			}

			return nil
		},
		func(err error) bool {
			var httpErr *retryableHTTPError
			if ok := errorAs(err, &httpErr); ok {
				return httpErr.StatusCode == http.StatusTooManyRequests || httpErr.StatusCode >= 500
			}
			return true
		},
		c.logger,
	)

	if err != nil {
		return domain.SearchResponse{}, fmt.Errorf("logo.dev search failed for query %q: %w", query, err)
	}

	return response, nil
}

func (c *Client) FetchLogo(ctx context.Context, domainName string) ([]byte, error) {
	var logoBytes []byte

	err := shared.Retry(
		ctx,
		shared.DefaultRetryPolicy,
		func(ctx context.Context) error {
			req, err := http.NewRequestWithContext(
				ctx,
				http.MethodGet,
				fmt.Sprintf("%s/%s?token=%s&size=128&format=png", c.imageBaseURL, domainName, c.token),
				nil,
			)
			if err != nil {
				return err
			}

			httpResp, err := c.httpClient.Do(req)
			if err != nil {
				return err
			}
			defer httpResp.Body.Close()

			if httpResp.StatusCode < 200 || httpResp.StatusCode > 299 {
				return &retryableHTTPError{StatusCode: httpResp.StatusCode}
			}

			logoBytes, err = io.ReadAll(httpResp.Body)
			if err != nil {
				return fmt.Errorf("read body: %w", err)
			}
			if len(logoBytes) == 0 {
				return fmt.Errorf("logo.dev returned empty body for domain %q", domainName)
			}

			return nil
		},
		func(err error) bool {
			var httpErr *retryableHTTPError
			if ok := errorAs(err, &httpErr); ok {
				return httpErr.StatusCode == http.StatusTooManyRequests || httpErr.StatusCode >= 500
			}
			return true
		},
		c.logger,
	)
	if err != nil {
		return nil, fmt.Errorf("logo.dev fetch failed for domain %q: %w", domainName, err)
	}

	return logoBytes, nil
}

type retryableHTTPError struct {
	StatusCode int
}

func (e *retryableHTTPError) Error() string {
	return fmt.Sprintf("unexpected HTTP status %d", e.StatusCode)
}

func errorAs(err error, target interface{}) bool {
	switch t := target.(type) {
	case **retryableHTTPError:
		httpErr, ok := err.(*retryableHTTPError)
		if ok {
			*t = httpErr
		}
		return ok
	default:
		return false
	}
}
