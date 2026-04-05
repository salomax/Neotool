package http

import (
	"archive/zip"
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/go-rod/rod"
	"github.com/go-rod/rod/lib/launcher"
	"github.com/go-rod/rod/lib/proto"
	"golang.org/x/time/rate"

	"github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist/internal/shared"
)

// B3 official series page — visited first so Chromium establishes a normal
// browsing session (cookies, referrer context) before hitting the file CDN.
const b3SeriesHistoricasReferer = "https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/historico/mercado-a-vista/series-historicas/"

// ErrFileNotFound is returned when B3 returns HTTP 404 for a given date's file.
// The caller (dispatcher) treats this as a skip, not a fatal error.
var ErrFileNotFound = errors.New("b3: file not found (404)")

// B3Client downloads COTAHIST ZIP files from the B3 historical data portal
// using headless Chromium (real TLS, cookies, and navigation semantics).
//
// File name conventions:
//
//	Annual:  COTAHIST_A{YYYY}.ZIP     e.g. COTAHIST_A2026.ZIP
//	Daily:   COTAHIST_D{DDMMYYYY}.ZIP e.g. COTAHIST_D17032026.ZIP
//
// B3's CDN (Cloudflare) returns 520 errors when downloads are started too close
// together. downloadLimiter enforces a minimum gap between consecutive transfers
// — independently of the worker pool size — so processing (parse + DB write)
// still runs fully in parallel, only the browser download phase is rate-limited.
type B3Client struct {
	baseURL         string
	timeout         time.Duration
	retry           shared.RetryPolicy
	downloadLimiter *rate.Limiter
	logger          *slog.Logger
	chromeBin       string

	browserMu sync.Mutex
	launcher  *launcher.Launcher
	browser   *rod.Browser
}

// NewB3Client creates a client that uses headless Chromium for downloads.
//
// chromeBin may be empty: Rod then uses its default Chromium (downloaded once
// per platform) or the usual Chrome install. In Docker, set chromeBin to the
// system chromium path (e.g. /usr/bin/chromium) and install the package.
//
// downloadIntervalSeconds is the minimum gap between consecutive download
// starts. Use 0 to disable rate limiting. Values of 10–15 s are recommended
// for B3's Cloudflare-fronted CDN.
func NewB3Client(baseURL string, timeoutSec, downloadIntervalSeconds int, chromeBin string, logger *slog.Logger) *B3Client {
	var limiter *rate.Limiter
	if downloadIntervalSeconds > 0 {
		limiter = rate.NewLimiter(rate.Every(time.Duration(downloadIntervalSeconds)*time.Second), 1)
	} else {
		limiter = rate.NewLimiter(rate.Inf, 1)
	}
	return &B3Client{
		baseURL:         strings.TrimRight(baseURL, "/"),
		timeout:         time.Duration(timeoutSec) * time.Second,
		retry:           shared.B3DownloadRetryPolicy,
		downloadLimiter: limiter,
		logger:          logger,
		chromeBin:       strings.TrimSpace(chromeBin),
	}
}

// Close shuts down the Chromium process and launcher resources.
// Safe to call multiple times.
func (c *B3Client) Close() {
	c.browserMu.Lock()
	defer c.browserMu.Unlock()
	if c.browser != nil {
		_ = c.browser.Close()
		c.browser = nil
	}
	if c.launcher != nil {
		c.launcher.Cleanup()
		c.launcher = nil
	}
}

// DownloadAnnualToTempFile downloads COTAHIST_A{year}.ZIP to a temp file.
// Returns the path and a cleanup func (os.Remove). Caller must always invoke cleanup.
//
// Annual files can be 30–150 MB compressed. The timeout should be set
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

func (c *B3Client) ensureBrowser() error {
	c.browserMu.Lock()
	defer c.browserMu.Unlock()
	if c.browser != nil {
		return nil
	}

	l := launcher.New().
		Headless(true).
		Set("disable-gpu").
		Set("no-sandbox").
		Set("disable-setuid-sandbox").
		Set("disable-dev-shm-usage")

	if c.chromeBin != "" {
		l = l.Bin(c.chromeBin)
	}

	wsURL, err := l.Launch()
	if err != nil {
		return fmt.Errorf("launching chromium: %w", err)
	}

	browser := rod.New().ControlURL(wsURL)
	if err := browser.Connect(); err != nil {
		l.Cleanup()
		return fmt.Errorf("connecting to chromium: %w", err)
	}

	c.launcher = l
	c.browser = browser
	return nil
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
	if err := tmp.Close(); err != nil {
		removeFn()
		return "", func() {}, fmt.Errorf("closing temp file: %w", err)
	}

	url := c.baseURL + "/" + filename

	if err := c.downloadLimiter.Wait(ctx); err != nil {
		removeFn()
		return "", func() {}, ctx.Err()
	}

	if err := c.ensureBrowser(); err != nil {
		removeFn()
		return "", func() {}, err
	}

	downloadErr := shared.Retry(ctx, c.retry, func(ctx context.Context) error {
		if err := os.Remove(tmpPath); err != nil && !os.IsNotExist(err) {
			return fmt.Errorf("removing temp file before retry: %w", err)
		}
		recreate, err := os.OpenFile(tmpPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o600)
		if err != nil {
			return fmt.Errorf("recreate temp file: %w", err)
		}
		_ = recreate.Close()

		dlDir, err := os.MkdirTemp("", "b3-chrome-dl-*")
		if err != nil {
			return fmt.Errorf("mkdir download dir: %w", err)
		}
		defer func() { _ = os.RemoveAll(dlDir) }()

		dlCtx, cancel := context.WithTimeout(ctx, c.timeout)
		defer cancel()

		br := c.browser
		if br == nil {
			return errors.New("chromium browser not initialized")
		}

		browser := br.Context(dlCtx)

		page, err := browser.Page(proto.TargetCreateTarget{})
		if err != nil {
			return fmt.Errorf("new browser page: %w", err)
		}
		defer func() { _ = page.Close() }()

		wait := browser.WaitDownload(dlDir)

		page = page.Context(dlCtx).Timeout(c.timeout)

		if err := page.Navigate(b3SeriesHistoricasReferer); err != nil {
			return fmt.Errorf("navigate referer: %w", err)
		}
		if err := page.WaitLoad(); err != nil {
			return fmt.Errorf("wait referer load: %w", err)
		}

		// Direct navigation to a ZIP often returns net::ERR_ABORTED: Chromium cancels
		// the document load when the response is a download. That is not a failure;
		// WaitDownload still receives the file if the transfer started.
		if err := page.Navigate(url); err != nil && !isNavigateDownloadAbort(err) {
			return fmt.Errorf("navigate file url: %w", err)
		}

		info := wait()
		if info == nil {
			if errors.Is(dlCtx.Err(), context.DeadlineExceeded) || errors.Is(dlCtx.Err(), context.Canceled) {
				if ctx.Err() != nil {
					return ctx.Err()
				}
			}
			html, _ := page.HTML()
			lower := strings.ToLower(html)
			if strings.Contains(lower, "404") || strings.Contains(lower, "not found") ||
				strings.Contains(lower, "não encontrado") || strings.Contains(lower, "http error 404") {
				return fmt.Errorf("%w: %s", ErrFileNotFound, filename)
			}
			return fmt.Errorf("download did not complete for %s (timeout or blocked)", filename)
		}

		srcPath := filepath.Join(dlDir, info.GUID)
		if err := copyFileToPath(srcPath, tmpPath); err != nil {
			return fmt.Errorf("copy download to temp: %w", err)
		}

		if err := verifyZipHeader(tmpPath); err != nil {
			return err
		}

		return nil
	}, isRetryable, c.logger)

	if downloadErr != nil {
		removeFn()
		return "", func() {}, downloadErr
	}
	return tmpPath, removeFn, nil
}

func isNavigateDownloadAbort(err error) bool {
	if err == nil {
		return false
	}
	s := err.Error()
	return strings.Contains(s, "ERR_ABORTED") || strings.Contains(s, "download is starting")
}

func copyFileToPath(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(dst, os.O_WRONLY|os.O_TRUNC, 0o600)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(out, in)
	closeErr := out.Close()
	if copyErr != nil {
		return copyErr
	}
	return closeErr
}

func verifyZipHeader(path string) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()
	hdr := make([]byte, 4)
	if _, err := io.ReadFull(f, hdr); err != nil {
		return fmt.Errorf("reading downloaded file header: %w", err)
	}
	if hdr[0] != 'P' || hdr[1] != 'K' {
		return fmt.Errorf("response is not a ZIP archive (expected PK header)")
	}
	return nil
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
