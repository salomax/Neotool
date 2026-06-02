package http

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/shared"
)

type LogoDevImageClient struct {
	imageBaseURL string
	apiKey       string
	httpClient   *http.Client
	logger       *slog.Logger
}

func NewLogoDevImageClient(imageBaseURL string, apiKey string, timeoutSeconds int, logger *slog.Logger) *LogoDevImageClient {
	return &LogoDevImageClient{
		imageBaseURL: strings.TrimRight(imageBaseURL, "/"),
		apiKey:       apiKey,
		httpClient: &http.Client{
			Timeout: time.Duration(timeoutSeconds) * time.Second,
		},
		logger: logger,
	}
}

func (c *LogoDevImageClient) FetchLogo(ctx context.Context, domainName string) ([]byte, error) {
	var logoBytes []byte

	err := shared.Retry(
		ctx,
		shared.DefaultRetryPolicy,
		func(ctx context.Context) error {
			req, err := http.NewRequestWithContext(
				ctx,
				http.MethodGet,
				fmt.Sprintf("%s/%s?token=%s&size=128&format=png", c.imageBaseURL, domainName, c.apiKey),
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
