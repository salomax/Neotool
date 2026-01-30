package source

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
)

type sgsItem struct {
	Date  string `json:"data"`
	Value string `json:"valor"`
}

// SGSClient fetches series from BCB SGS API
type SGSClient struct {
	httpClient   *http.Client
	minInterval  time.Duration
	lastRequest  time.Time
	mu           sync.Mutex
}

func NewSGSClient(timeout time.Duration, minInterval time.Duration) *SGSClient {
	return &SGSClient{
		httpClient:  &http.Client{Timeout: timeout},
		minInterval: minInterval,
	}
}

// ValidateSeries checks SGS metadata endpoint availability for the series
func (c *SGSClient) ValidateSeries(ctx context.Context, metadataURL string) error {
	if metadataURL == "" {
		return fmt.Errorf("sgs metadata url is empty")
	}
	if err := c.throttle(ctx); err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, metadataURL, nil)
	if err != nil {
		return fmt.Errorf("sgs metadata request build failed: %w", err)
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("sgs metadata request failed: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("sgs metadata status %d: %s", resp.StatusCode, string(body))
	}
	return nil
}

// FetchValues fetches and normalizes SGS values
func (c *SGSClient) FetchValues(ctx context.Context, variationID, url string) ([]domain.ValueRecord, error) {
	if err := c.throttle(ctx); err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("sgs request build failed: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("sgs request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("sgs response status %d: %s", resp.StatusCode, string(body))
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("sgs read failed: %w", err)
	}

	var raw []sgsItem
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, fmt.Errorf("sgs json decode failed: %w", err)
	}

	values := make([]domain.ValueRecord, 0, len(raw))
	for _, row := range raw {
		if row.Date == "" || row.Value == "" {
			continue
		}
		refDate, err := time.Parse("02/01/2006", row.Date)
		if err != nil {
			continue
		}

		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refDate.UTC(),
			PublicationDate:      nil,
			Value:                normalizeDecimal(row.Value),
			SourceMeta: map[string]any{
				"source":       "SGS",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
			},
		})
	}

	return values, nil
}

func (c *SGSClient) throttle(ctx context.Context) error {
	if c.minInterval <= 0 {
		return nil
	}
	c.mu.Lock()
	if c.lastRequest.IsZero() {
		c.lastRequest = time.Now()
		c.mu.Unlock()
		return nil
	}
	elapsed := time.Since(c.lastRequest)
	if elapsed >= c.minInterval {
		c.lastRequest = time.Now()
		c.mu.Unlock()
		return nil
	}
	wait := c.minInterval - elapsed
	c.mu.Unlock()

	select {
	case <-time.After(wait):
		c.mu.Lock()
		c.lastRequest = time.Now()
		c.mu.Unlock()
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func normalizeDecimal(val string) string {
	if strings.Contains(val, ",") {
		return strings.ReplaceAll(val, ",", ".")
	}
	if _, err := strconv.ParseFloat(val, 64); err == nil {
		return val
	}
	return val
}
