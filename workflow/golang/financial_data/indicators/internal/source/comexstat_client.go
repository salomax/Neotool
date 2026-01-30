package source

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
)

type comexResponse struct {
	Data struct {
		List []map[string]any `json:"list"`
	} `json:"data"`
}

type comexRequest struct {
	Flow        string   `json:"flow"`
	MonthDetail bool     `json:"monthDetail"`
	Period      struct {
		From string `json:"from"`
		To   string `json:"to"`
	} `json:"period"`
	Filters []map[string]any `json:"filters"`
	Details []string         `json:"details"`
	Metrics []string         `json:"metrics"`
}

// ComexStatClient fetches trade balance data from Comex Stat API
type ComexStatClient struct {
	httpClient *http.Client
	headers    map[string]string
	minInterval time.Duration
	lastRequest time.Time
	mu          sync.Mutex
	maxRetries  int
	retryWait   time.Duration
}

func NewComexStatClient(timeout time.Duration, minInterval time.Duration, maxRetries int, retryWait time.Duration, headers map[string]string) *ComexStatClient {
	copyHeaders := make(map[string]string, len(headers))
	for k, v := range headers {
		if v == "" {
			continue
		}
		copyHeaders[k] = v
	}
	return &ComexStatClient{
		httpClient:  &http.Client{Timeout: timeout},
		headers:     copyHeaders,
		minInterval: minInterval,
		maxRetries:  maxRetries,
		retryWait:   retryWait,
	}
}

// FetchValues fetches and normalizes Comex Stat values
func (c *ComexStatClient) FetchValues(ctx context.Context, variationID string, url string, body []byte) ([]domain.ValueRecord, error) {
	var reqPayload comexRequest
	if err := json.Unmarshal(body, &reqPayload); err != nil {
		return nil, fmt.Errorf("comexstat request decode failed: %w", err)
	}

	if reqPayload.Flow == "balance" {
		exportReq := reqPayload
		exportReq.Flow = "export"
		importReq := reqPayload
		importReq.Flow = "import"

		exportData, err := c.fetchComexRows(ctx, url, exportReq)
		if err != nil {
			return nil, err
		}
		importData, err := c.fetchComexRows(ctx, url, importReq)
		if err != nil {
			return nil, err
		}
		return c.mergeBalance(variationID, url, exportData, importData), nil
	}

	rows, err := c.fetchComexRows(ctx, url, reqPayload)
	if err != nil {
		return nil, err
	}
	return c.toValueRecords(variationID, url, rows), nil
}

func (c *ComexStatClient) fetchComexRows(ctx context.Context, url string, reqPayload comexRequest) ([]map[string]any, error) {
	body, err := json.Marshal(reqPayload)
	if err != nil {
		return nil, fmt.Errorf("comexstat request encode failed: %w", err)
	}

	var payload []byte
	for attempt := 0; attempt <= c.maxRetries; attempt++ {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
		if err != nil {
			return nil, fmt.Errorf("comexstat request build failed: %w", err)
		}
		if err := c.throttle(ctx); err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", "application/json")
		for k, v := range c.headers {
			req.Header.Set(k, v)
		}

		resp, err := c.httpClient.Do(req)
		if err != nil {
			return nil, fmt.Errorf("comexstat request failed: %w", err)
		}

		payload, _ = io.ReadAll(resp.Body)
		resp.Body.Close()

		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			break
		}
		if resp.StatusCode != http.StatusTooManyRequests || attempt == c.maxRetries {
			return nil, fmt.Errorf("comexstat response status %d: %s", resp.StatusCode, string(payload))
		}

		wait := c.retryWait
		if retryAfter := resp.Header.Get("Retry-After"); retryAfter != "" {
			if seconds, err := strconv.Atoi(retryAfter); err == nil {
				wait = time.Duration(seconds) * time.Second
			}
		}
		if err := c.sleepWithContext(ctx, wait); err != nil {
			return nil, err
		}
	}

	if payload == nil {
		return nil, fmt.Errorf("comexstat response empty")
	}

	var decoded comexResponse
	if err := json.Unmarshal(payload, &decoded); err != nil {
		return nil, fmt.Errorf("comexstat json decode failed: %w", err)
	}

	return decoded.Data.List, nil
}

func (c *ComexStatClient) toValueRecords(variationID string, url string, rows []map[string]any) []domain.ValueRecord {
	values := make([]domain.ValueRecord, 0, len(rows))
	for _, row := range rows {
		refDate, ok := parseComexMonth(row)
		if !ok {
			continue
		}
		value := pickString(row, "metricFOB", "valor", "saldo")
		if value == "" {
			continue
		}
		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refDate.UTC(),
			PublicationDate:      nil,
			Value:                normalizeDecimal(value),
			SourceMeta: map[string]any{
				"source":       "COMEXSTAT",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
			},
		})
	}
	return values
}

func (c *ComexStatClient) mergeBalance(variationID string, url string, exports, imports []map[string]any) []domain.ValueRecord {
	exportMap := make(map[string]float64, len(exports))
	importMap := make(map[string]float64, len(imports))

	for _, row := range exports {
		refDate, ok := parseComexMonth(row)
		if !ok {
			continue
		}
		value := pickString(row, "metricFOB")
		if value == "" {
			continue
		}
		if parsed, err := strconv.ParseFloat(normalizeDecimal(value), 64); err == nil {
			exportMap[refDate.Format("2006-01")] = parsed
		}
	}

	for _, row := range imports {
		refDate, ok := parseComexMonth(row)
		if !ok {
			continue
		}
		value := pickString(row, "metricFOB")
		if value == "" {
			continue
		}
		if parsed, err := strconv.ParseFloat(normalizeDecimal(value), 64); err == nil {
			importMap[refDate.Format("2006-01")] = parsed
		}
	}

	values := make([]domain.ValueRecord, 0, len(exportMap))
	for period, exportValue := range exportMap {
		refDate, err := time.Parse("2006-01", period)
		if err != nil {
			continue
		}
		importValue := importMap[period]
		balance := exportValue - importValue
		values = append(values, domain.ValueRecord{
			IndicatorVariationID: variationID,
			ReferenceDate:        refDate.UTC(),
			PublicationDate:      nil,
			Value:                strconv.FormatFloat(balance, 'f', -1, 64),
			SourceMeta: map[string]any{
				"source":       "COMEXSTAT",
				"url":          url,
				"retrieved_at": time.Now().UTC().Format(time.RFC3339),
				"computed":     "export_minus_import",
			},
		})
	}

	return values
}

func (c *ComexStatClient) throttle(ctx context.Context) error {
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

func (c *ComexStatClient) sleepWithContext(ctx context.Context, wait time.Duration) error {
	if wait <= 0 {
		return nil
	}
	select {
	case <-time.After(wait):
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func parseComexMonth(row map[string]any) (time.Time, bool) {
	yearStr := pickString(row, "year", "ano")
	monthStr := pickString(row, "monthNumber", "month", "mes")
	if yearStr == "" || monthStr == "" {
		return time.Time{}, false
	}
	month, err := strconv.Atoi(monthStr)
	if err != nil || month < 1 || month > 12 {
		return time.Time{}, false
	}
	period := fmt.Sprintf("%s-%02d", yearStr, month)
	refDate, err := time.Parse("2006-01", period)
	if err != nil {
		return time.Time{}, false
	}
	return refDate, true
}

func pickString(row map[string]any, keys ...string) string {
	for _, k := range keys {
		if v, ok := row[k]; ok {
			switch t := v.(type) {
			case string:
				return t
			case float64:
				return strconv.FormatFloat(t, 'f', -1, 64)
			}
		}
	}
	return ""
}
