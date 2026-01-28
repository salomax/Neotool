package source

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sort"
	"strconv"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/domain"
)

type ipeadataResponse struct {
	Value []struct {
		SeriesCode string `json:"SERCODIGO"`
		Date       string `json:"VALDATA"`
		Value      any    `json:"VALVALOR"`
	} `json:"value"`
}

// IpeadataClient fetches series from Ipeadata OData API
type IpeadataClient struct {
	httpClient *http.Client
	baseURL    string
}

func NewIpeadataClient(timeout time.Duration, baseURL string) *IpeadataClient {
	return &IpeadataClient{
		httpClient: &http.Client{Timeout: timeout},
		baseURL:    baseURL,
	}
}

// FetchValues fetches and normalizes Ipeadata values
func (c *IpeadataClient) FetchValues(ctx context.Context, variationID string, seriesCode string, start *time.Time, end *time.Time, calcType string) ([]domain.ValueRecord, error) {
	if seriesCode == "" {
		return nil, fmt.Errorf("ipeadata series code is empty")
	}
	url := fmt.Sprintf("%s/ValoresSerie(SERCODIGO='%s')", c.baseURL, seriesCode)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("ipeadata request build failed: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("ipeadata request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("ipeadata response status %d: %s", resp.StatusCode, string(body))
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("ipeadata read failed: %w", err)
	}

	var decoded ipeadataResponse
	if err := json.Unmarshal(body, &decoded); err != nil {
		return nil, fmt.Errorf("ipeadata json decode failed: %w", err)
	}

	if calcType != "" {
		return c.computeFromIndex(variationID, url, seriesCode, decoded.Value, start, end, calcType)
	}

	values := make([]domain.ValueRecord, 0, len(decoded.Value))
	for _, row := range decoded.Value {
		if row.Date == "" {
			continue
		}
		refDate, err := parseIpeadataDate(row.Date)
		if err != nil {
			continue
		}
		if start != nil && refDate.Before(*start) {
			continue
		}
		if end != nil && refDate.After(*end) {
			continue
		}

		valueStr := parseIpeadataValue(row.Value)
		if valueStr == "" {
			continue
		}

		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refDate.UTC(),
			PublicationDate:      nil,
			Value:                normalizeDecimal(valueStr),
			SourceMeta: map[string]any{
				"source":       "IPEADATA",
				"url":          url,
				"series_code":  seriesCode,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
			},
		})
	}

	return values, nil
}

func (c *IpeadataClient) computeFromIndex(variationID string, url string, seriesCode string, rows []struct {
	SeriesCode string `json:"SERCODIGO"`
	Date       string `json:"VALDATA"`
	Value      any    `json:"VALVALOR"`
}, start *time.Time, end *time.Time, calcType string) ([]domain.ValueRecord, error) {
	index := make(map[time.Time]float64, len(rows))
	dates := make([]time.Time, 0, len(rows))

	for _, row := range rows {
		if row.Date == "" {
			continue
		}
		refDate, err := parseIpeadataDate(row.Date)
		if err != nil {
			continue
		}
		valStr := parseIpeadataValue(row.Value)
		if valStr == "" {
			continue
		}
		val, err := strconv.ParseFloat(normalizeDecimal(valStr), 64)
		if err != nil {
			continue
		}
		monthRef := time.Date(refDate.Year(), refDate.Month(), 1, 0, 0, 0, 0, time.UTC)
		if _, ok := index[monthRef]; !ok {
			dates = append(dates, monthRef)
		}
		index[monthRef] = val
	}

	sort.Slice(dates, func(i, j int) bool { return dates[i].Before(dates[j]) })

	values := make([]domain.ValueRecord, 0, len(dates))
	for _, refDate := range dates {
		if start != nil && refDate.Before(*start) {
			continue
		}
		if end != nil && refDate.After(*end) {
			continue
		}

		var baseDate time.Time
		switch calcType {
		case "YTD":
			baseDate = time.Date(refDate.Year()-1, time.December, 1, 0, 0, 0, 0, time.UTC)
		case "ROLLING_12M":
			baseDate = refDate.AddDate(0, -12, 0)
		default:
			continue
		}

		baseValue, ok := index[baseDate]
		if !ok || baseValue == 0 {
			continue
		}
		currentValue := index[refDate]
		percent := (currentValue/baseValue - 1) * 100

		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refDate.UTC(),
			PublicationDate:      nil,
			Value:                strconv.FormatFloat(percent, 'f', -1, 64),
			SourceMeta: map[string]any{
				"source":       "IPEADATA",
				"url":          url,
				"series_code":  seriesCode,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
				"computed":     calcType,
				"base_date":    baseDate.Format("2006-01-02"),
			},
		})
	}

	return values, nil
}

func parseIpeadataDate(val string) (time.Time, error) {
	if t, err := time.Parse(time.RFC3339, val); err == nil {
		return t, nil
	}
	if t, err := time.Parse("2006-01-02", val); err == nil {
		return t, nil
	}
	return time.Time{}, fmt.Errorf("invalid ipeadata date: %s", val)
}

func parseIpeadataValue(val any) string {
	switch v := val.(type) {
	case string:
		return v
	case float64:
		return strconv.FormatFloat(v, 'f', -1, 64)
	case json.Number:
		return v.String()
	default:
		return ""
	}
}
