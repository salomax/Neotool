package http

import (
	"archive/zip"
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"time"

	"golang.org/x/time/rate"

	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/shared"
)

// ErrFileNotFound is returned when B3 returns HTTP 404 for a given date's file.
// The caller (dispatcher) treats this as a skip, not a fatal error.
var ErrFileNotFound = errors.New("b3: file not found (404)")

// B3Client downloads COTAHIST ZIP files from the B3 historical data portal.
//
// File name conventions:
//
//	Annual:  COTAHIST_A{YYYY}.ZIP     e.g. COTAHIST_A2026.ZIP
//	Daily:   COTAHIST_D{DDMMYYYY}.ZIP e.g. COTAHIST_D17032026.ZIP
//
// B3's CDN (Cloudflare) returns 520 errors when downloads are started too close
// together. downloadLimiter enforces a minimum gap between consecutive HTTP
// transfers — independently of the worker pool size — so processing (parse +
// DB write) still runs fully in parallel, only the HTTP transfer phase is
// rate-limited.
type B3Client struct {
	baseURL         string
	httpClient      *http.Client
	retry           shared.RetryPolicy
	downloadLimiter *rate.Limiter // one token consumed per download attempt
	logger          *slog.Logger
}

// NewB3Client creates a client with a rate-limited downloader.
//
// downloadIntervalSeconds is the minimum gap between consecutive download
// starts. Use 0 to disable rate limiting (unlimited). Values of 10–15 s are
// recommended for B3's Cloudflare-fronted CDN.
func NewB3Client(baseURL string, timeoutSec, downloadIntervalSeconds int, logger *slog.Logger) *B3Client {
	var limiter *rate.Limiter
	if downloadIntervalSeconds > 0 {
		limiter = rate.NewLimiter(rate.Every(time.Duration(downloadIntervalSeconds)*time.Second), 1)
	} else {
		limiter = rate.NewLimiter(rate.Inf, 1)
	}
	return &B3Client{
		baseURL:         strings.TrimRight(baseURL, "/"),
		httpClient:      &http.Client{Timeout: time.Duration(timeoutSec) * time.Second},
		retry:           shared.B3DownloadRetryPolicy,
		downloadLimiter: limiter,
		logger:          logger,
	}
}

// DownloadAnnualToTempFile downloads COTAHIST_A{year}.ZIP to a temp file.
// Returns the path and a cleanup func (os.Remove). Caller must always invoke cleanup.
//
// Annual files can be 30–150 MB compressed. The HTTP timeout should be set
// generously (300 s) to accommodate slow B3 servers.
func (c *B3Client) DownloadAnnualToTempFile(ctx context.Context, year int) (string, func(), error) {
	filename := fmt.Sprintf("COTAHIST_A%d.ZIP", year)
	return c.downloadToTempFile(ctx, filename, fmt.Sprintf("b3-annual-%d-*.zip", year))
}

// DownloadMonthlyToTempFile downloads COTAHIST_M{MMYYYY}.ZIP to a temp file.
// e.g. February 2026 → COTAHIST_M022026.ZIP
func (c *B3Client) DownloadMonthlyToTempFile(ctx context.Context, year int, month time.Month) (string, func(), error) {
	filename := fmt.Sprintf("COTAHIST_M%02d%d.ZIP", int(month), year)
	return c.downloadToTempFile(ctx, filename, fmt.Sprintf("b3-monthly-%d%02d-*.zip", year, int(month)))
}

// DownloadDailyToTempFile downloads COTAHIST_D{DDMMYYYY}.ZIP to a temp file.
// Returns ErrFileNotFound (wrapped) when the date has no file (weekend / holiday).
func (c *B3Client) DownloadDailyToTempFile(ctx context.Context, date time.Time) (string, func(), error) {
	filename := fmt.Sprintf("COTAHIST_D%s.ZIP", date.Format("02012006"))
	return c.downloadToTempFile(ctx, filename, fmt.Sprintf("b3-daily-%s-*.zip", date.Format("20060102")))
}

// downloadToTempFile downloads a file by name to a temp file and returns its path
// plus a cleanup function. The download is retried on transient failures.
//
// The rate limiter token is consumed once per call (before the retry loop) so
// that successive files are spaced out. Individual retry attempts within the
// same call run at the retry policy's own backoff cadence.
func (c *B3Client) downloadToTempFile(ctx context.Context, filename, pattern string) (string, func(), error) {
	tmp, err := os.CreateTemp("", pattern)
	if err != nil {
		return "", func() {}, fmt.Errorf("creating temp file: %w", err)
	}
	tmpPath := tmp.Name()
	removeFn := func() { _ = os.Remove(tmpPath) }

	url := c.baseURL + "/" + filename

	// Wait for a rate-limiter token before starting the HTTP transfer.
	// This enforces a minimum gap between consecutive downloads, preventing
	// Cloudflare 520 errors caused by too many parallel or rapid-fire requests.
	if err := c.downloadLimiter.Wait(ctx); err != nil {
		_ = tmp.Close()
		removeFn()
		return "", func() {}, ctx.Err()
	}

	downloadErr := shared.Retry(ctx, c.retry, func(ctx context.Context) error {
		// Truncate on each retry so we start with a clean file.
		if err := tmp.Truncate(0); err != nil {
			return fmt.Errorf("truncating temp file: %w", err)
		}
		if _, err := tmp.Seek(0, io.SeekStart); err != nil {
			return fmt.Errorf("seeking temp file: %w", err)
		}

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
		if err != nil {
			return err
		}

		// Mimic a real Chrome browser so Cloudflare does not fingerprint and
		// rate-limit the Go default user-agent.
		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
		req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
		req.Header.Set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
		// Accept-Encoding is intentionally omitted: Go's transport adds it
		// automatically and handles decompression transparently, which avoids
		// receiving gzip-wrapped bytes when writing a binary ZIP to disk.
		req.Header.Set("Referer", "https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/historico/mercado-a-vista/series-historicas/")
		req.Header.Set("Upgrade-Insecure-Requests", "1")
		req.Header.Set("Sec-Fetch-Dest", "document")
		req.Header.Set("Sec-Fetch-Mode", "navigate")
		req.Header.Set("Sec-Fetch-Site", "same-site")
		req.Header.Set("Sec-Fetch-User", "?1")
		req.Header.Set("Sec-Ch-Ua", `"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"`)
		req.Header.Set("Sec-Ch-Ua-Mobile", "?0")
		req.Header.Set("Sec-Ch-Ua-Platform", `"Windows"`)

		resp, err := c.httpClient.Do(req)
		if err != nil {
			return fmt.Errorf("GET %s: %w", url, err)
		}
		defer resp.Body.Close()

		if resp.StatusCode == http.StatusNotFound {
			// 404 is not retryable — return sentinel so caller can skip the date.
			return fmt.Errorf("%w: %s", ErrFileNotFound, filename)
		}
		if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("GET %s: unexpected status %d", url, resp.StatusCode)
		}

		if _, err = io.Copy(tmp, resp.Body); err != nil {
			return fmt.Errorf("writing ZIP to temp file: %w", err)
		}
		return nil
	}, isRetryable, c.logger)

	if downloadErr != nil {
		_ = tmp.Close()
		removeFn()
		return "", func() {}, downloadErr
	}
	if err = tmp.Close(); err != nil {
		removeFn()
		return "", func() {}, fmt.Errorf("closing temp file: %w", err)
	}
	return tmpPath, removeFn, nil
}

// OpenTXTFromZip opens the first .TXT entry inside a COTAHIST ZIP file and
// returns a reader over its raw bytes (Windows-1252, fixed-width lines).
// The caller must close both the returned ReadCloser and zip.ReadCloser.
//
// B3 names the inner file "COTAHIST_A2026.TXT" (or .D…), so we match on .txt
// case-insensitively.
func OpenTXTFromZip(zipPath string) (*zip.ReadCloser, io.ReadCloser, error) {
	zr, err := zip.OpenReader(zipPath)
	if err != nil {
		return nil, nil, fmt.Errorf("opening ZIP %s: %w", zipPath, err)
	}

	for _, f := range zr.File {
		if strings.HasSuffix(strings.ToLower(f.Name), ".txt") {
			rc, err := f.Open()
			if err != nil {
				_ = zr.Close()
				return nil, nil, fmt.Errorf("opening TXT entry %s: %w", f.Name, err)
			}
			return zr, rc, nil
		}
	}

	_ = zr.Close()
	return nil, nil, fmt.Errorf("no .TXT file found in ZIP %s", zipPath)
}

// isRetryable returns true for transient errors.
// 404 (ErrFileNotFound) and context cancellation are not retried.
func isRetryable(err error) bool {
	if errors.Is(err, ErrFileNotFound) {
		return false
	}
	return !errors.Is(err, context.Canceled) && !errors.Is(err, context.DeadlineExceeded)
}
