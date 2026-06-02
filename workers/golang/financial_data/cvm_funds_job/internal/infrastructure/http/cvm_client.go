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

	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/shared"
)

// CVMClient downloads files from the CVM open data portal.
type CVMClient struct {
	baseURL    string
	httpClient *http.Client
	retry      shared.RetryPolicy
	logger     *slog.Logger
}

func NewCVMClient(baseURL string, timeoutSec int, logger *slog.Logger) *CVMClient {
	return &CVMClient{
		baseURL:    baseURL,
		httpClient: &http.Client{Timeout: time.Duration(timeoutSec) * time.Second},
		retry:      shared.DefaultRetryPolicy,
		logger:     logger,
	}
}

// StreamFundCatalog returns a streaming ReadCloser for cad_fi.csv.
// The caller is responsible for closing the body.
// The response is NOT buffered — pass directly to encoding/csv for row-by-row streaming.
func (c *CVMClient) StreamFundCatalog(ctx context.Context) (io.ReadCloser, error) {
	return c.openStream(ctx, "/dados/FI/CAD/DADOS/cad_fi.csv")
}

// DownloadFundClasseCatalogToTempFile downloads registro_fundo_classe.zip to a temp file.
// This file contains funds registered under RCVM 175 (new format, post-2024) and must
// be processed alongside cad_fi.csv to get the full fund universe.
func (c *CVMClient) DownloadFundClasseCatalogToTempFile(ctx context.Context) (string, func(), error) {
	return c.downloadZipToTempFile(ctx, "/dados/FI/CAD/DADOS/registro_fundo_classe.zip", "cvm-catalog-classe-*.zip")
}

// DownloadNavToTempFile downloads inf_diario_fi_{yearMonth}.zip to a temp file.
// Returns the file path and a cleanup function (os.Remove).
//
// Why a temp file instead of memory?
//
//	archive/zip.OpenReader requires an io.ReaderAt (seekable), which an HTTP response
//	body is not. Writing the ZIP to disk keeps peak heap bounded to one chunk buffer
//	(NAVChunkSize × ~80 bytes) regardless of the file size (~15 MB compressed).
//
// The caller must invoke cleanup() when done — even on error paths.
// The download is retried on transient failures using the client's retry policy.
func (c *CVMClient) DownloadNavToTempFile(ctx context.Context, yearMonth string) (string, func(), error) {
	url := fmt.Sprintf("/dados/FI/DOC/INF_DIARIO/DADOS/inf_diario_fi_%s.zip", yearMonth)
	return c.downloadZipToTempFile(ctx, url, "cvm-nav-*.zip")
}

// downloadZipToTempFile downloads a ZIP from the given CVM path to a temporary file and
// returns the file path plus a cleanup function that removes it. The download is retried
// on transient failures using the client's retry policy.
func (c *CVMClient) downloadZipToTempFile(ctx context.Context, url, pattern string) (string, func(), error) {
	tmp, err := os.CreateTemp("", pattern)
	if err != nil {
		return "", func() {}, fmt.Errorf("creating temp file: %w", err)
	}
	tmpPath := tmp.Name()
	removeFn := func() { _ = os.Remove(tmpPath) }

	downloadErr := shared.Retry(ctx, c.retry, func(ctx context.Context) error {
		// Truncate on each retry so we start with a clean file.
		if err := tmp.Truncate(0); err != nil {
			return fmt.Errorf("truncating temp file: %w", err)
		}
		if _, err := tmp.Seek(0, io.SeekStart); err != nil {
			return fmt.Errorf("seeking temp file: %w", err)
		}
		body, err := c.openStream(ctx, url)
		if err != nil {
			return err
		}
		defer body.Close()
		if _, err = io.Copy(tmp, body); err != nil {
			return fmt.Errorf("writing ZIP to temp file: %w", err)
		}
		return nil
	}, isRetryable, c.logger)

	if downloadErr != nil {
		_ = tmp.Close()
		removeFn()
		return "", func() {}, fmt.Errorf("downloading %s: %w", url, downloadErr)
	}
	if err = tmp.Close(); err != nil {
		removeFn()
		return "", func() {}, fmt.Errorf("closing temp file: %w", err)
	}
	return tmpPath, removeFn, nil
}

// OpenFirstCSVFromZip opens the first CSV entry inside a ZIP file.
// Returns the ReadCloser and the zip.ReadCloser (caller must close both).
func OpenFirstCSVFromZip(zipPath string) (*zip.ReadCloser, io.ReadCloser, error) {
	zr, err := zip.OpenReader(zipPath)
	if err != nil {
		return nil, nil, fmt.Errorf("opening ZIP %s: %w", zipPath, err)
	}

	for _, f := range zr.File {
		if strings.HasSuffix(strings.ToLower(f.Name), ".csv") {
			rc, err := f.Open()
			if err != nil {
				_ = zr.Close()
				return nil, nil, fmt.Errorf("opening CSV entry %s: %w", f.Name, err)
			}
			return zr, rc, nil
		}
	}

	_ = zr.Close()
	return nil, nil, fmt.Errorf("no CSV file found in ZIP %s", zipPath)
}

func (c *CVMClient) openStream(ctx context.Context, path string) (io.ReadCloser, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+path, nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("GET %s: %w", path, err)
	}
	if resp.StatusCode != http.StatusOK {
		_ = resp.Body.Close()
		return nil, fmt.Errorf("GET %s: unexpected status %d", path, resp.StatusCode)
	}
	return resp.Body, nil
}

// isRetryable returns true for transient errors. Context cancellation is not retried.
func isRetryable(err error) bool {
	return !errors.Is(err, context.Canceled) && !errors.Is(err, context.DeadlineExceeded)
}
