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

	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
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
// If quarterlyAdjust is true, dates are adjusted to the last month of the quarter (03, 06, 09, 12)
func (c *IpeadataClient) FetchValues(ctx context.Context, variationID string, seriesCode string, start *time.Time, end *time.Time, calcType string, quarterlyAdjust bool) ([]domain.ValueRecord, error) {
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

		finalDate := refDate
		if quarterlyAdjust {
			finalDate = adjustToQuarterEnd(refDate)
		}

		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        finalDate.UTC(),
			PublicationDate:      nil,
			Value:                normalizeDecimal(valueStr),
			AdjustedValue:        nil,
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
	type indexPoint struct {
		value float64
		raw   string
	}
	index := make(map[time.Time]indexPoint, len(rows))
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
		raw := normalizeDecimal(valStr)
		val, err := strconv.ParseFloat(raw, 64)
		if err != nil {
			continue
		}
		monthRef := time.Date(refDate.Year(), refDate.Month(), 1, 0, 0, 0, 0, time.UTC)
		if _, ok := index[monthRef]; !ok {
			dates = append(dates, monthRef)
		}
		index[monthRef] = indexPoint{value: val, raw: raw}
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

		basePoint, ok := index[baseDate]
		if !ok || basePoint.value == 0 {
			continue
		}
		currentPoint, ok := index[refDate]
		if !ok {
			continue
		}
		percent := (currentPoint.value/basePoint.value - 1) * 100
		adjusted := strconv.FormatFloat(percent, 'f', -1, 64)

		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refDate.UTC(),
			PublicationDate:      nil,
			Value:                currentPoint.raw,
			AdjustedValue:        &adjusted,
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

// adjustToQuarterEnd converts a quarter-start date (01, 04, 07, 10) to quarter-end (03, 06, 09, 12)
func adjustToQuarterEnd(t time.Time) time.Time {
	month := t.Month()
	switch month {
	case time.January:
		return time.Date(t.Year(), time.March, 1, 0, 0, 0, 0, time.UTC)
	case time.April:
		return time.Date(t.Year(), time.June, 1, 0, 0, 0, 0, time.UTC)
	case time.July:
		return time.Date(t.Year(), time.September, 1, 0, 0, 0, 0, time.UTC)
	case time.October:
		return time.Date(t.Year(), time.December, 1, 0, 0, 0, 0, time.UTC)
	default:
		return t // Not a quarter-start month, return as-is
	}
}
