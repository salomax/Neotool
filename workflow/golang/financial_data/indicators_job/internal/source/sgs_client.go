package source

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sort"
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
	httpClient  *http.Client
	minInterval time.Duration
	lastRequest time.Time
	mu          sync.Mutex
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
	return c.FetchValuesWithCalc(ctx, variationID, url, "", nil, nil)
}

// FetchValuesWithCalc fetches SGS values with optional computation (e.g., ROLLING_12M)
func (c *SGSClient) FetchValuesWithCalc(ctx context.Context, variationID, url, calcType string, start, end *time.Time) ([]domain.ValueRecord, error) {
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

	if resp.StatusCode == http.StatusNotFound {
		body, _ := io.ReadAll(resp.Body)
		if strings.Contains(string(body), "Value(s) not found") {
			return []domain.ValueRecord{}, nil
		}
		return nil, fmt.Errorf("sgs response status %d: %s", resp.StatusCode, string(body))
	}

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
		// Try to parse as error object
		var errResp struct {
			Erro struct {
				Detail string `json:"detail"`
			} `json:"erro"`
		}
		if json.Unmarshal(body, &errResp) == nil && errResp.Erro.Detail != "" {
			fmt.Printf("DEBUG: SGS error detail: %q\n", errResp.Erro.Detail)
			if strings.Contains(strings.ToLower(errResp.Erro.Detail), "value(s) not found") {
				return []domain.ValueRecord{}, nil
			}
			return nil, fmt.Errorf("sgs api error: %s", errResp.Erro.Detail)
		}

		logBody := string(body)
		if len(logBody) > 500 {
			logBody = logBody[:500] + "..."
		}
		return nil, fmt.Errorf("sgs json decode failed: %w (body: %s)", err, logBody)
	}

	// If no computation needed, return raw values
	if calcType == "" {
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

	// For YTD computation from monthly rates (accumulate from January)
	if calcType == "YTD" {
		return c.computeYTD(variationID, url, raw, start, end)
	}

	// For ROLLING_12M computation from monthly rates
	if calcType == "ROLLING_12M" {
		return c.computeRolling12M(variationID, url, raw, start, end)
	}

	// For SUM_YTD: sum monthly values from January to current month
	if calcType == "SUM_YTD" {
		return c.computeSumYTD(variationID, url, raw, start, end)
	}

	// For SUM_ROLLING_12M: sum monthly values over a rolling 12-month window
	if calcType == "SUM_ROLLING_12M" {
		return c.computeSumRolling12M(variationID, url, raw, start, end)
	}

	// For MONTHLY_LAST: aggregate daily values to monthly (last value of each month)
	if calcType == "MONTHLY_LAST" {
		return c.computeMonthlyLast(variationID, url, raw)
	}

	return nil, fmt.Errorf("unsupported SGS calcType: %s", calcType)
}

// parseMonthlyRates parses raw SGS items into a sorted map of monthly rates
func (c *SGSClient) parseMonthlyRates(raw []sgsItem) (map[time.Time]float64, []time.Time) {
	monthRates := make(map[time.Time]float64)
	var sortedMonths []time.Time

	for _, row := range raw {
		if row.Date == "" || row.Value == "" {
			continue
		}
		refDate, err := time.Parse("02/01/2006", row.Date)
		if err != nil {
			continue
		}
		valStr := normalizeDecimal(row.Value)
		val, err := strconv.ParseFloat(valStr, 64)
		if err != nil {
			continue
		}
		// Normalize to first of month
		monthKey := time.Date(refDate.Year(), refDate.Month(), 1, 0, 0, 0, 0, time.UTC)
		if _, exists := monthRates[monthKey]; !exists {
			sortedMonths = append(sortedMonths, monthKey)
		}
		monthRates[monthKey] = val
	}

	// Sort months chronologically
	sort.Slice(sortedMonths, func(i, j int) bool {
		return sortedMonths[i].Before(sortedMonths[j])
	})

	return monthRates, sortedMonths
}

// computeYTD computes year-to-date accumulated return from monthly percentage rates
// Formula: product of (1 + r_i/100) from January to current month, then (product - 1) * 100
func (c *SGSClient) computeYTD(variationID, url string, raw []sgsItem, start, end *time.Time) ([]domain.ValueRecord, error) {
	monthRates, sortedMonths := c.parseMonthlyRates(raw)

	values := make([]domain.ValueRecord, 0)
	for _, refMonth := range sortedMonths {
		// Check if within requested date range
		if start != nil && refMonth.Before(*start) {
			continue
		}
		if end != nil && refMonth.After(*end) {
			continue
		}

		// Compute YTD: accumulate from January of the same year
		product := 1.0
		year := refMonth.Year()
		for m := time.January; m <= refMonth.Month(); m++ {
			monthKey := time.Date(year, m, 1, 0, 0, 0, 0, time.UTC)
			rate, ok := monthRates[monthKey]
			if !ok {
				continue // Skip missing months
			}
			product *= (1 + rate/100)
		}

		// Convert to percentage: (product - 1) * 100
		ytd := (product - 1) * 100
		ytdStr := strconv.FormatFloat(ytd, 'f', 2, 64)

		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refMonth.UTC(),
			PublicationDate:      nil,
			Value:                ytdStr,
			AdjustedValue:        nil,
			SourceMeta: map[string]any{
				"source":       "SGS",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
				"computed":     "YTD",
			},
		})
	}

	return values, nil
}

// computeRolling12M computes 12-month rolling return from monthly percentage rates
// Formula: product of (1 + r_i/100) for 12 months, then (product - 1) * 100
func (c *SGSClient) computeRolling12M(variationID, url string, raw []sgsItem, start, end *time.Time) ([]domain.ValueRecord, error) {
	monthRates, sortedMonths := c.parseMonthlyRates(raw)

	// Compute rolling 12M for each month that has 12 months of history
	values := make([]domain.ValueRecord, 0)
	for i := 11; i < len(sortedMonths); i++ {
		refMonth := sortedMonths[i]

		// Check if within requested date range
		if start != nil && refMonth.Before(*start) {
			continue
		}
		if end != nil && refMonth.After(*end) {
			continue
		}

		// Compute product of (1 + r/100) for last 12 months
		product := 1.0
		validMonths := 0
		for j := i - 11; j <= i; j++ {
			monthKey := sortedMonths[j]
			rate, ok := monthRates[monthKey]
			if !ok {
				break
			}
			product *= (1 + rate/100)
			validMonths++
		}

		if validMonths != 12 {
			continue // Skip if we don't have full 12 months
		}

		// Convert to percentage: (product - 1) * 100
		rolling12M := (product - 1) * 100
		rolling12MStr := strconv.FormatFloat(rolling12M, 'f', 2, 64)

		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refMonth.UTC(),
			PublicationDate:      nil,
			Value:                rolling12MStr,
			AdjustedValue:        nil,
			SourceMeta: map[string]any{
				"source":       "SGS",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
				"computed":     "ROLLING_12M",
			},
		})
	}

	return values, nil
}

// computeSumYTD computes year-to-date accumulated sum from monthly absolute values
// Formula: sum of values from January to current month of the same year
func (c *SGSClient) computeSumYTD(variationID, url string, raw []sgsItem, start, end *time.Time) ([]domain.ValueRecord, error) {
	monthValues, sortedMonths := c.parseMonthlyRates(raw)

	values := make([]domain.ValueRecord, 0)
	for _, refMonth := range sortedMonths {
		if start != nil && refMonth.Before(*start) {
			continue
		}
		if end != nil && refMonth.After(*end) {
			continue
		}

		sum := 0.0
		year := refMonth.Year()
		for m := time.January; m <= refMonth.Month(); m++ {
			monthKey := time.Date(year, m, 1, 0, 0, 0, 0, time.UTC)
			val, ok := monthValues[monthKey]
			if !ok {
				continue
			}
			sum += val
		}

		sumStr := strconv.FormatFloat(sum, 'f', 2, 64)
		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refMonth.UTC(),
			PublicationDate:      nil,
			Value:                sumStr,
			SourceMeta: map[string]any{
				"source":       "SGS",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
				"computed":     "SUM_YTD",
			},
		})
	}

	return values, nil
}

// computeSumRolling12M computes 12-month rolling sum from monthly absolute values
// Formula: sum of values for the last 12 months
func (c *SGSClient) computeSumRolling12M(variationID, url string, raw []sgsItem, start, end *time.Time) ([]domain.ValueRecord, error) {
	monthValues, sortedMonths := c.parseMonthlyRates(raw)

	values := make([]domain.ValueRecord, 0)
	for i := 11; i < len(sortedMonths); i++ {
		refMonth := sortedMonths[i]

		if start != nil && refMonth.Before(*start) {
			continue
		}
		if end != nil && refMonth.After(*end) {
			continue
		}

		sum := 0.0
		validMonths := 0
		for j := i - 11; j <= i; j++ {
			monthKey := sortedMonths[j]
			val, ok := monthValues[monthKey]
			if !ok {
				break
			}
			sum += val
			validMonths++
		}

		if validMonths != 12 {
			continue
		}

		sumStr := strconv.FormatFloat(sum, 'f', 2, 64)
		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refMonth.UTC(),
			PublicationDate:      nil,
			Value:                sumStr,
			SourceMeta: map[string]any{
				"source":       "SGS",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
				"computed":     "SUM_ROLLING_12M",
			},
		})
	}

	return values, nil
}

// computeMonthlyLast aggregates daily values to monthly by taking the last value of each month
func (c *SGSClient) computeMonthlyLast(variationID, url string, raw []sgsItem) ([]domain.ValueRecord, error) {
	// Group values by month, keeping track of the latest date and value for each month
	type monthData struct {
		latestDate time.Time
		value      string
	}
	monthValues := make(map[time.Time]*monthData)
	var sortedMonths []time.Time

	for _, row := range raw {
		if row.Date == "" || row.Value == "" {
			continue
		}
		refDate, err := time.Parse("02/01/2006", row.Date)
		if err != nil {
			continue
		}

		// Normalize to first of month as the key
		monthKey := time.Date(refDate.Year(), refDate.Month(), 1, 0, 0, 0, 0, time.UTC)

		existing, exists := monthValues[monthKey]
		if !exists {
			sortedMonths = append(sortedMonths, monthKey)
			monthValues[monthKey] = &monthData{
				latestDate: refDate,
				value:      row.Value,
			}
		} else if refDate.After(existing.latestDate) {
			// Keep the latest date's value
			existing.latestDate = refDate
			existing.value = row.Value
		}
	}

	// Sort months chronologically
	sort.Slice(sortedMonths, func(i, j int) bool {
		return sortedMonths[i].Before(sortedMonths[j])
	})

	// Build output records
	values := make([]domain.ValueRecord, 0, len(sortedMonths))
	for _, monthKey := range sortedMonths {
		data := monthValues[monthKey]
		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        monthKey.UTC(),
			PublicationDate:      nil,
			Value:                normalizeDecimal(data.value),
			SourceMeta: map[string]any{
				"source":       "SGS",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
				"computed":     "MONTHLY_LAST",
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
