package bacen

import (
	"bytes"
	"context"
	"encoding/csv"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/invistus/bacen-ifdata/internal/shared"
	"golang.org/x/sync/semaphore"
)

const (
	// BACEN API constants
	DefaultBaseURL        = "https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/odata/"
	DefaultTimeout        = 60 * time.Second
	DefaultPageSize       = 10000
	RegistryPageSize      = 2000
	CatalogFormat         = "text/csv"
	DataFormat            = "application/json"

	// Endpoints
	EndpointIfDataCadastro = "IfDataCadastro"
	EndpointListaDeRelatorio = "ListaDeRelatorio"
	EndpointIfDataValores = "IfDataValores"
	
	// Selected fields for report data (reduces payload size)
	// Note: CodInst is required per-record, AnoMes and TipoInstituicao are filter params (not needed in response)
	ReportDataSelectFields = "CodInst,Grupo,Conta,NomeColuna,DescricaoColuna,Saldo"
)

// ReportDataResponse represents the JSON response from BACEN API
type ReportDataResponse struct {
	Value []ReportDataRecord `json:"value"`
}

// ReportDataRecord represents a single record in the BACEN API response
type ReportDataRecord struct {
	TipoInstituicao int      `json:"TipoInstituicao"`
	CodInst         string   `json:"CodInst"`
	AnoMes          string   `json:"AnoMes"`
	NomeRelatorio   string   `json:"NomeRelatorio"`
	NumeroRelatorio string   `json:"NumeroRelatorio"`
	Grupo           *string  `json:"Grupo"`
	Conta           *string  `json:"Conta"`
	NomeColuna      *string  `json:"NomeColuna"`
	DescricaoColuna *string  `json:"DescricaoColuna"`
	Saldo           *float64 `json:"Saldo"`
}

// Client is the HTTP client for BACEN API
type Client struct {
	baseURL        string
	httpClient     *http.Client
	retryPolicy    shared.RetryPolicy
	circuitBreaker *shared.CircuitBreaker
	rateLimiter    *shared.RateLimiter
	semaphore      *semaphore.Weighted // Limits concurrent requests to BACEN
	logger         *slog.Logger
}

// NewClient creates a new BACEN API client with circuit breaker and rate limiter
func NewClient(baseURL string, timeout time.Duration, retryPolicy shared.RetryPolicy, maxRequestsPerSecond float64, burstSize int, logger *slog.Logger) *Client {
	if baseURL == "" {
		baseURL = DefaultBaseURL
	}

	if timeout == 0 {
		timeout = DefaultTimeout
	}

	// Create circuit breaker with sensible defaults
	circuitBreaker := shared.NewCircuitBreaker(shared.CircuitBreakerConfig{
		MaxFailures:         5,  // Open after 5 consecutive failures
		Timeout:             60 * time.Second, // Wait 60s before trying again
		MaxHalfOpenRequests: 3,  // Allow 3 test requests in half-open state
	})

	// Create rate limiter to prevent API backpressure
	rateLimiter := shared.NewRateLimiter(maxRequestsPerSecond, burstSize)

	// Create semaphore to limit concurrent requests (BACEN doesn't support parallel requests)
	// Using 1 to enforce sequential processing
	sem := semaphore.NewWeighted(1)

	return &Client{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: timeout,
		},
		retryPolicy:    retryPolicy,
		circuitBreaker: circuitBreaker,
		rateLimiter:    rateLimiter,
		semaphore:      sem,
		logger:         logger,
	}
}

// FetchRegistry fetches institution registry for a specific quarter
func (c *Client) FetchRegistry(ctx context.Context, anoMes string) (string, error) {
	c.logger.Info("fetching institution registry",
		"ano_mes", anoMes)

	// Build query string using @ for function parameters, $ for OData query options
	// Format: IfDataCadastro(AnoMes=@AnoMes)?@AnoMes=202503&$filter=Situacao eq 'A'&$format=text/csv&$top=2000
	endpoint := fmt.Sprintf("%s(AnoMes=@AnoMes)?@AnoMes=%s&$filter=Situacao%%20eq%%20'A'&$format=%s&$top=%d",
		EndpointIfDataCadastro,
		anoMes,
		CatalogFormat,
		RegistryPageSize)

	data, err := c.fetchWithPagination(ctx, endpoint, RegistryPageSize)
	if err != nil {
		return "", fmt.Errorf("failed to fetch registry: %w", err)
	}

	c.logger.Info("successfully fetched institution registry",
		"ano_mes", anoMes,
		"data_size", len(data))

	return data, nil
}

// FetchRegistryPages fetches institution registry page by page, calling the callback for each page
// This allows processing data in chunks instead of loading everything into memory
func (c *Client) FetchRegistryPages(
	ctx context.Context,
	anoMes string,
	pageCallback func(header []string, rows [][]string) error,
) error {
	c.logger.Info("fetching institution registry pages",
		"ano_mes", anoMes)

	// Build query string using @ for function parameters, $ for OData query options
	baseEndpoint := fmt.Sprintf("%s(AnoMes=@AnoMes)?@AnoMes=%s&$filter=Situacao%%20eq%%20'A'&$format=%s&$top=%d",
		EndpointIfDataCadastro,
		anoMes,
		CatalogFormat,
		RegistryPageSize)

	skip := 0
	maxSkip := 1000000 // Safety limit
	var header []string
	totalRows := 0

	for {
		// Check context cancellation before each iteration
		select {
		case <-ctx.Done():
			return fmt.Errorf("context cancelled during pagination: %w", ctx.Err())
		default:
		}

		// Safety check: prevent infinite loops
		if skip >= maxSkip {
			c.logger.Warn("pagination limit reached, stopping",
				"skip", skip,
				"max_skip", maxSkip)
			break
		}

		// Add pagination parameters using @ (not $)
		endpoint := baseEndpoint
		if skip > 0 {
			endpoint += fmt.Sprintf("&$skip=%d", skip)
		}

		c.logger.Info("fetching registry page",
			"skip", skip,
			"page_size", RegistryPageSize)

		data, err := c.fetch(ctx, endpoint)
		if err != nil {
			return fmt.Errorf("failed to fetch page at skip %d: %w", skip, err)
		}

		// Parse CSV
		pageHeader, rows, err := shared.ParseCSVWithHeader(data)
		if err != nil {
			return fmt.Errorf("failed to parse CSV response: %w", err)
		}

		// Store header from first page
		if skip == 0 {
			header = pageHeader
		}

		// Process this page immediately
		if len(rows) > 0 {
			if err := pageCallback(header, rows); err != nil {
				return fmt.Errorf("page callback failed at skip %d: %w", skip, err)
			}
		}

		// Track total rows
		totalRows += len(rows)

		// Log page summary
		c.logger.Info("page processed",
			"skip", skip,
			"rows", len(rows),
			"page_size", RegistryPageSize,
			"total_rows_so_far", totalRows)

		// Check if we have more pages
		// If we got fewer rows than pageSize, we're definitely done
		if len(rows) < RegistryPageSize {
			c.logger.Info("pagination complete - got fewer rows than page size",
				"total_skip", skip,
				"final_rows", len(rows),
				"page_size", RegistryPageSize)
			break
		}

		// If we got exactly pageSize rows, continue to next page
		// The next iteration will check if there's more data
		// Safety limit (maxSkip) prevents infinite loops
		skip += RegistryPageSize
	}

	c.logger.Info("finished fetching registry pages",
		"total_pages", (skip/RegistryPageSize)+1,
		"total_rows", totalRows,
		"final_skip", skip)

	return nil
}

// FetchReportCatalog fetches the list of available reports
func (c *Client) FetchReportCatalog(ctx context.Context) (string, error) {
	c.logger.Info("fetching report catalog")

	// Build query string using @ parameters (not $)
	// Format: ListaDeRelatorio()?@format=text/csv
	endpoint := fmt.Sprintf("%s()?@format=%s", EndpointListaDeRelatorio, CatalogFormat)

	data, err := c.fetch(ctx, endpoint)
	if err != nil {
		return "", fmt.Errorf("failed to fetch report catalog: %w", err)
	}

	c.logger.Info("successfully fetched report catalog",
		"data_size", len(data))

	return data, nil
}

// FetchReportData fetches financial data for a specific report with pagination
// Returns JSON string with selected fields only
func (c *Client) FetchReportData(ctx context.Context, anoMes string, tipoInstituicao int, numeroRelatorio string, pageSkip, pageTop int) (string, error) {
	start := time.Now()

	c.logger.Info("fetching report data",
		"ano_mes", anoMes,
		"tipo_instituicao", tipoInstituicao,
		"numero_relatorio", numeroRelatorio,
		"page_skip", pageSkip,
		"page_top", pageTop)

	// Build query string with JSON format and $select parameter
	// Format: ?$format=application/json&$select=Grupo,Conta,NomeColuna,DescricaoColuna,Saldo&$skip=0&$top=5000&@AnoMes=202503&@Relatorio='5'&@TipoInstituicao=2
	// Note: @ parameters (not $), and Relatorio value needs quotes
	endpoint := fmt.Sprintf("%s(AnoMes=@AnoMes,TipoInstituicao=@TipoInstituicao,Relatorio=@Relatorio)?$format=%s&$select=%s&$skip=%d&$top=%d&@AnoMes=%s&@Relatorio='%s'&@TipoInstituicao=%d",
		EndpointIfDataValores,
		DataFormat,
		ReportDataSelectFields,
		pageSkip,
		pageTop,
		anoMes,
		numeroRelatorio,
		tipoInstituicao)

	data, err := c.fetch(ctx, endpoint)
	if err != nil {
		return "", fmt.Errorf("failed to fetch report data: %w", err)
	}

	duration := time.Since(start)
	c.logger.Info("successfully fetched report data",
		"ano_mes", anoMes,
		"tipo_instituicao", tipoInstituicao,
		"numero_relatorio", numeroRelatorio,
		"page_skip", pageSkip,
		"data_size", len(data),
		"duration_ms", duration.Milliseconds())

	return data, nil
}

// fetch performs an HTTP GET request with rate limiting, circuit breaker and retry logic
func (c *Client) fetch(ctx context.Context, endpoint string) (string, error) {
	startTime := time.Now()
	fullURL := c.baseURL + endpoint

	var responseData string

	// Acquire semaphore to limit concurrent requests (BACEN doesn't support parallel requests)
	if err := c.semaphore.Acquire(ctx, 1); err != nil {
		return "", fmt.Errorf("semaphore acquire failed: %w", err)
	}
	defer c.semaphore.Release(1)

	// Wait for rate limiter before attempting request
	if err := c.rateLimiter.Wait(ctx); err != nil {
		return "", fmt.Errorf("rate limiter wait failed: %w", err)
	}

	// Wrap entire operation with circuit breaker
	err := c.circuitBreaker.Execute(func() error {
		// Retry logic within circuit breaker
		return shared.Retry(
			ctx,
			c.retryPolicy,
			func(ctx context.Context) error {
				req, err := http.NewRequestWithContext(ctx, http.MethodGet, fullURL, nil)
				if err != nil {
					return shared.WrapNonRetryableError(fmt.Errorf("failed to create request: %w", err))
				}

				// Set Accept header based on endpoint
				// For report data, use JSON; for registry/catalog, use CSV
				if strings.Contains(fullURL, EndpointIfDataValores) {
					req.Header.Set("Accept", "application/json")
				} else {
					req.Header.Set("Accept", "text/csv")
				}
				req.Header.Set("User-Agent", "bacen-ifdata-go/1.0")

				resp, err := c.httpClient.Do(req)
				if err != nil {
					return fmt.Errorf("HTTP request failed: %w", err)
				}
				defer resp.Body.Close()

				// Check status code
				if resp.StatusCode != http.StatusOK {
					body, _ := io.ReadAll(resp.Body)
					httpErr := &HTTPError{
						StatusCode: resp.StatusCode,
						Message:    string(body),
					}

					// HTTP 500 with "Erro desconhecido" means BACEN is down
					// Make this non-retryable to fail fast
					if resp.StatusCode == http.StatusInternalServerError {
						c.logger.Error("BACEN server error detected (likely service is down)",
							"status_code", resp.StatusCode,
							"message", string(body))
						return shared.WrapNonRetryableError(httpErr)
					}

					return httpErr
				}

				// Read response body
				bodyBytes, err := io.ReadAll(resp.Body)
				if err != nil {
					return fmt.Errorf("failed to read response body: %w", err)
				}

				responseData = string(bodyBytes)
				return nil
			},
			func(err error) bool {
				if shared.IsExplicitlyNonRetryable(err) {
					return false
				}
				return shared.IsRetryableError(err)
			},
			c.logger,
		)
	})

	duration := time.Since(startTime)

	if err != nil {
		if errors.Is(err, shared.ErrCircuitOpen) {
			c.logger.Warn("circuit breaker is open, fast-failing request",
				"url", fullURL,
				"state", c.circuitBreaker.GetState().String(),
				"consecutive_failures", c.circuitBreaker.GetConsecutiveFailures(),
				"duration_ms", duration.Milliseconds())
		}
		return "", err
	}

	// Parse CSV to count rows for summary
	rowCount := 0
	if len(responseData) > 0 {
		_, rows, parseErr := shared.ParseCSVWithHeader(responseData)
		if parseErr == nil {
			rowCount = len(rows)
		}
	}

	// Log summary with timing and row count
	c.logger.Info("request completed",
		"url", fullURL,
		"duration_ms", duration.Milliseconds(),
		"duration", duration.Round(time.Millisecond).String(),
		"rows", rowCount,
		"data_size_bytes", len(responseData))

	return responseData, nil
}

// fetchWithPagination handles automatic pagination for endpoints that support it
func (c *Client) fetchWithPagination(ctx context.Context, baseEndpoint string, pageSize int) (string, error) {
	startTime := time.Now()
	var allData string
	skip := 0
	maxSkip := 1000000 // Safety limit: prevent infinite loops
	totalRows := 0

	c.logger.Info("starting pagination",
		"base_endpoint", baseEndpoint,
		"page_size", pageSize)

	for {
		// Check context cancellation before each iteration
		select {
		case <-ctx.Done():
			return "", fmt.Errorf("context cancelled during pagination: %w", ctx.Err())
		default:
		}

		// Safety check: prevent infinite loops
		if skip >= maxSkip {
			c.logger.Warn("pagination limit reached, stopping",
				"skip", skip,
				"max_skip", maxSkip)
			break
		}

		// Add pagination parameters using $ for OData query options
		endpoint := baseEndpoint
		if skip > 0 {
			endpoint += fmt.Sprintf("&$skip=%d", skip)
		}

		c.logger.Info("fetching paginated page",
			"skip", skip,
			"page_size", pageSize,
			"endpoint", endpoint)

		data, err := c.fetch(ctx, endpoint)
		if err != nil {
			return "", fmt.Errorf("failed to fetch page at skip %d: %w", skip, err)
		}

		// Parse to count rows
		_, rows, err := shared.ParseCSVWithHeader(data)
		if err != nil {
			return "", fmt.Errorf("failed to parse CSV response: %w", err)
		}

		totalRows += len(rows)

		// Append data (skip header after first page)
		if skip == 0 {
			allData = data
		} else {
			// Only append data rows, not header
			if len(rows) > 0 {
				// Use csv.Writer for proper escaping of commas, quotes, newlines
				var buf bytes.Buffer
				csvWriter := csv.NewWriter(&buf)
				for _, row := range rows {
					if err := csvWriter.Write(row); err != nil {
						return "", fmt.Errorf("failed to write CSV row: %w", err)
					}
				}
				csvWriter.Flush()
				if err := csvWriter.Error(); err != nil {
					return "", fmt.Errorf("CSV writer error: %w", err)
				}
				allData += buf.String()
			}
		}

		// Check if we have more pages
		// If we got fewer rows than pageSize, we're definitely done
		if len(rows) < pageSize {
			c.logger.Info("pagination complete - got fewer rows than page size",
				"total_skip", skip,
				"final_rows", len(rows),
				"page_size", pageSize)
			break
		}

		// If we got exactly pageSize rows, continue to next page
		// The next iteration will check if there's more data
		// Safety limit (maxSkip) prevents infinite loops
		skip += pageSize
	}

	duration := time.Since(startTime)
	c.logger.Info("pagination finished",
		"total_pages", (skip/pageSize)+1,
		"total_rows", totalRows,
		"final_skip", skip,
		"data_size_bytes", len(allData),
		"duration_ms", duration.Milliseconds(),
		"duration", duration.Round(time.Millisecond).String())

	return allData, nil
}

// HTTPError represents an HTTP error with status code
type HTTPError struct {
	StatusCode int
	Message    string
}

func (e *HTTPError) Error() string {
	return fmt.Sprintf("HTTP %d: %s", e.StatusCode, e.Message)
}

// IsHTTPError checks if an error is an HTTP error
func IsHTTPError(err error) bool {
	_, ok := err.(*HTTPError)
	return ok
}

// GetHTTPStatusCode extracts status code from error (works with wrapped errors)
func GetHTTPStatusCode(err error) int {
	var httpErr *HTTPError
	if errors.As(err, &httpErr) {
		return httpErr.StatusCode
	}
	return 0
}
