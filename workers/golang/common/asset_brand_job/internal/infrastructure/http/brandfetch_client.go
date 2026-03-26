package http

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	nethttp "net/http"
	"net/url"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/shared"
)

var brandfetchAttemptTypes = []string{"", "logo", "symbol"}

type BrandfetchClient struct {
	baseURL    string
	clientID   string
	httpClient *nethttp.Client
	logger     *slog.Logger
}

type brandfetchAttemptPayload struct {
	Type       string `json:"type"`
	URL        string `json:"url"`
	StatusCode int    `json:"statusCode"`
}

type brandfetchLookupPayload struct {
	Provider     string                     `json:"provider"`
	AssetType    string                     `json:"assetType"`
	IsinCode     string                     `json:"isinCode"`
	ResolvedType string                     `json:"resolvedType,omitempty"`
	Attempts     []brandfetchAttemptPayload `json:"attempts"`
}

func NewBrandfetchClient(baseURL string, clientID string, timeoutSeconds int, logger *slog.Logger) *BrandfetchClient {
	return &BrandfetchClient{
		baseURL:  strings.TrimRight(baseURL, "/"),
		clientID: clientID,
		httpClient: &nethttp.Client{
			Timeout: time.Duration(timeoutSeconds) * time.Second,
		},
		logger: logger,
	}
}

func (c *BrandfetchClient) LookupBrand(ctx context.Context, asset domain.PendingAsset) (*domain.BrandLookupResult, error) {
	isinCode := strings.TrimSpace(strings.ToUpper(asset.IsinCode))
	if isinCode == "" {
		return nil, nil
	}

	payload := brandfetchLookupPayload{
		Provider:  "BRANDFETCH_ISIN",
		AssetType: strings.TrimSpace(strings.ToUpper(asset.AssetType)),
		IsinCode:  isinCode,
		Attempts:  make([]brandfetchAttemptPayload, 0, len(brandfetchAttemptTypes)),
	}

	for _, assetType := range brandfetchAttemptTypes {
		lookupURL := c.buildLookupURL(isinCode, assetType)
		statusCode, result, err := c.fetchBrand(ctx, lookupURL)
		payload.Attempts = append(payload.Attempts, brandfetchAttemptPayload{
			Type:       brandfetchTypeLabel(assetType),
			URL:        lookupURL,
			StatusCode: statusCode,
		})
		if err != nil {
			return nil, fmt.Errorf("brandfetch lookup failed for isin %q: %w", isinCode, err)
		}
		if result == nil {
			continue
		}

		payload.ResolvedType = brandfetchTypeLabel(assetType)
		rawPayload, err := json.Marshal(payload)
		if err != nil {
			return nil, fmt.Errorf("marshal brandfetch payload: %w", err)
		}

		return &domain.BrandLookupResult{
			QueryUsed:  isinCode,
			LogoURL:    lookupURL,
			LogoData:   result.Data,
			MimeType:   result.MimeType,
			RawPayload: string(rawPayload),
		}, nil
	}

	c.logger.Info(
		"brandfetch lookup returned no image",
		"ticker", asset.Ticker,
		"asset_type", asset.AssetType,
		"isin_code", isinCode,
	)

	return nil, nil
}

func (c *BrandfetchClient) buildLookupURL(isinCode string, assetType string) string {
	path := fmt.Sprintf("%s/isin/%s/w/400/h/400", c.baseURL, url.PathEscape(isinCode))
	if assetType != "" {
		path += "/" + url.PathEscape(assetType)
	} else {
		path += "/"
	}
	return path + "?c=" + url.QueryEscape(c.clientID)
}

type brandfetchImageResult struct {
	Data     []byte
	MimeType string
}

func (c *BrandfetchClient) fetchBrand(ctx context.Context, lookupURL string) (int, *brandfetchImageResult, error) {
	var (
		image      *brandfetchImageResult
		statusCode int
	)

	err := shared.Retry(
		ctx,
		shared.DefaultRetryPolicy,
		func(ctx context.Context) error {
			req, err := nethttp.NewRequestWithContext(ctx, nethttp.MethodGet, lookupURL, nil)
			if err != nil {
				return err
			}

			httpResp, err := c.httpClient.Do(req)
			if err != nil {
				return err
			}
			defer httpResp.Body.Close()

			statusCode = httpResp.StatusCode
			if httpResp.StatusCode == nethttp.StatusNotFound {
				image = nil
				return nil
			}
			if httpResp.StatusCode < 200 || httpResp.StatusCode > 299 {
				return &retryableHTTPError{StatusCode: httpResp.StatusCode}
			}

			logoBytes, err := io.ReadAll(httpResp.Body)
			if err != nil {
				return fmt.Errorf("read body: %w", err)
			}
			if len(logoBytes) == 0 {
				image = nil
				return nil
			}

			mimeType := detectImageMimeType(httpResp.Header.Get("Content-Type"), logoBytes)
			if mimeType == "" {
				return fmt.Errorf("unsupported image mime type for %q", lookupURL)
			}
			image = &brandfetchImageResult{Data: logoBytes, MimeType: mimeType}
			return nil
		},
		func(err error) bool {
			var httpErr *retryableHTTPError
			if ok := errorAs(err, &httpErr); ok {
				return httpErr.StatusCode == nethttp.StatusTooManyRequests || httpErr.StatusCode >= 500
			}
			return true
		},
		c.logger,
	)
	if err != nil {
		return statusCode, nil, err
	}
	if image == nil || len(image.Data) == 0 {
		return statusCode, nil, nil
	}
	return statusCode, image, nil
}

func brandfetchTypeLabel(assetType string) string {
	if assetType == "" {
		return "default"
	}
	return assetType
}

func detectImageMimeType(headerValue string, data []byte) string {
	mimeType := strings.TrimSpace(headerValue)
	if mimeType != "" {
		if idx := strings.Index(mimeType, ";"); idx >= 0 {
			mimeType = strings.TrimSpace(mimeType[:idx])
		}
		switch mimeType {
		case "image/jpeg", "image/png", "image/webp", "image/svg+xml":
			return mimeType
		}
	}

	if len(data) >= 3 && data[0] == 0xFF && data[1] == 0xD8 && data[2] == 0xFF {
		return "image/jpeg"
	}
	if len(data) >= 4 && data[0] == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 {
		return "image/png"
	}
	if len(data) >= 12 && string(data[0:4]) == "RIFF" && string(data[8:12]) == "WEBP" {
		return "image/webp"
	}

	prefixBytes := data[:minInt(len(data), 256)]
	prefix := string(prefixBytes)
	if utf8.Valid(prefixBytes) {
		snippet := strings.TrimSpace(prefix)
		if strings.HasPrefix(snippet, "<?xml") || strings.HasPrefix(snippet, "<svg") {
			return "image/svg+xml"
		}
	}

	return ""
}

func minInt(a int, b int) int {
	if a < b {
		return a
	}
	return b
}
