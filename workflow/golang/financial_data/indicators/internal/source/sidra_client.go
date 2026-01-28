package source

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/domain"
)

// SIDRAClient fetches series from IBGE SIDRA
type SIDRAClient struct {
	httpClient *http.Client
}

func NewSIDRAClient(timeout time.Duration) *SIDRAClient {
	return &SIDRAClient{
		httpClient: &http.Client{Timeout: timeout},
	}
}

// FetchValues fetches and normalizes SIDRA values
func (c *SIDRAClient) FetchValues(ctx context.Context, variationID, url string) ([]domain.ValueRecord, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("sidra request build failed: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("sidra request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("sidra response status %d: %s", resp.StatusCode, string(body))
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("sidra read failed: %w", err)
	}

	var raw []map[string]string
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, fmt.Errorf("sidra json decode failed: %w", err)
	}

	values := make([]domain.ValueRecord, 0, len(raw))
	for _, row := range raw {
		period := strings.TrimSpace(row["D3C"])
		value := strings.TrimSpace(row["V"])
		if period == "" || value == "" || period == "D3C" {
			continue
		}
		refDate, err := parseSIDRAPeriod(period)
		if err != nil {
			continue
		}

		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refDate,
			PublicationDate:      nil,
			Value:                value,
			SourceMeta: map[string]any{
				"source":       "SIDRA",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
				"raw_period":   period,
			},
		})
	}

	return values, nil
}

func parseSIDRAPeriod(period string) (time.Time, error) {
	if len(period) == 6 {
		y, err := strconv.Atoi(period[0:4])
		if err != nil {
			return time.Time{}, err
		}
		m, err := strconv.Atoi(period[4:6])
		if err != nil {
			return time.Time{}, err
		}
		return time.Date(y, time.Month(m), 1, 0, 0, 0, 0, time.UTC), nil
	}
	if len(period) == 4 {
		y, err := strconv.Atoi(period)
		if err != nil {
			return time.Time{}, err
		}
		return time.Date(y, time.January, 1, 0, 0, 0, 0, time.UTC), nil
	}
	return time.Time{}, fmt.Errorf("unsupported period format: %s", period)
}
