package processor

import (
	"context"
	"fmt"
	"log/slog"
	"net/url"
	"strings"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/assets"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/logo"
)

// LogoProcessor handles logo download and upload
type LogoProcessor struct {
	logoDownloader *logo.Client
	assetUploader  *assets.Client
	logger         *slog.Logger
}

// NewLogoProcessor creates a new logo processor
func NewLogoProcessor(
	logoDownloader *logo.Client,
	assetUploader *assets.Client,
	logger *slog.Logger,
) *LogoProcessor {
	return &LogoProcessor{
		logoDownloader: logoDownloader,
		assetUploader:  assetUploader,
		logger:         logger,
	}
}

// ProcessLogo processes a logo for the given website URL
// Returns the asset ID (UUID string) if successful, or nil if processing fails (graceful degradation)
func (lp *LogoProcessor) ProcessLogo(ctx context.Context, website *string) *string {
	if website == nil || *website == "" {
		lp.logger.Info("Skipping logo processing - no website URL")
		return nil
	}

	// Extract domain from website URL
	domain := ExtractDomain(*website)
	if domain == "" {
		lp.logger.Warn("Failed to extract domain from website URL", "website", *website)
		return nil
	}

	lp.logger.Info("Processing logo", "domain", domain, "website", *website)

	// Download logo from logo.dev
	logoData, err := lp.logoDownloader.DownloadLogo(ctx, domain)
	if err != nil {
		lp.logger.Warn("Failed to download logo",
			"domain", domain,
			"error", err)
		return nil
	}

	// Determine MIME type from logo data
	mimeType := detectMimeType(logoData)
	if mimeType == "" {
		lp.logger.Warn("Could not determine MIME type for logo",
			"domain", domain,
			"size_bytes", len(logoData))
		return nil
	}

	// Generate filename
	filename := fmt.Sprintf("%s.%s", domain, getFileExtension(mimeType))

	// Upload logo to assets service
	assetID, err := lp.assetUploader.UploadLogo(ctx, logoData, filename, mimeType)
	if err != nil {
		lp.logger.Warn("Failed to upload logo to assets service",
			"domain", domain,
			"error", err)
		return nil
	}

	lp.logger.Info("Successfully processed logo",
		"domain", domain,
		"asset_id", assetID)

	return &assetID
}

// ExtractDomain extracts a clean domain from a website URL.
// Removes protocol (http://, https://), www. prefix, paths, and query strings.
//
// Examples:
//   - "https://www.example.com/br/pt/aboutus/" -> "example.com"
//   - "http://example.com?language=1" -> "example.com"
//   - "https://www.example.com.br" -> "example.com.br"
func ExtractDomain(websiteURL string) string {
	if websiteURL == "" {
		return ""
	}

	// Parse URL to handle all edge cases
	parsedURL, err := url.Parse(websiteURL)
	if err != nil {
		// If parsing fails, try simple string manipulation
		return extractDomainSimple(websiteURL)
	}

	// Get hostname (this already removes protocol and path)
	domain := parsedURL.Hostname()

	// Remove www. prefix if present
	if strings.HasPrefix(strings.ToLower(domain), "www.") {
		domain = domain[4:]
	}

	return domain
}

// extractDomainSimple is a fallback for when URL parsing fails
func extractDomainSimple(websiteURL string) string {
	// Remove protocol
	domain := strings.TrimSpace(websiteURL)
	domain = strings.TrimPrefix(domain, "http://")
	domain = strings.TrimPrefix(domain, "https://")
	domain = strings.TrimPrefix(domain, "HTTP://")
	domain = strings.TrimPrefix(domain, "HTTPS://")

	// Remove www. prefix
	if strings.HasPrefix(strings.ToLower(domain), "www.") {
		domain = domain[4:]
	}

	// Remove path and query string (everything after first / or ?)
	if idx := strings.IndexAny(domain, "/?"); idx != -1 {
		domain = domain[:idx]
	}

	return domain
}

// detectMimeType detects MIME type from image data
func detectMimeType(data []byte) string {
	if len(data) < 4 {
		return ""
	}

	// Check magic bytes for common image formats
	// JPEG: FF D8 FF
	if len(data) >= 3 && data[0] == 0xFF && data[1] == 0xD8 && data[2] == 0xFF {
		return "image/jpeg"
	}

	// PNG: 89 50 4E 47
	if len(data) >= 4 && data[0] == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 {
		return "image/png"
	}

	// GIF: 47 49 46 38
	if len(data) >= 4 && data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x38 {
		return "image/gif"
	}

	// WebP: Check for "RIFF" header and "WEBP" chunk
	if len(data) >= 12 && string(data[0:4]) == "RIFF" && string(data[8:12]) == "WEBP" {
		return "image/webp"
	}

	// SVG: Check for XML/SVG declaration (may start with whitespace)
	svgStart := strings.TrimSpace(string(data[:min(100, len(data))]))
	if strings.HasPrefix(svgStart, "<?xml") || strings.HasPrefix(svgStart, "<svg") {
		return "image/svg+xml"
	}

	return ""
}

// getFileExtension returns file extension for a MIME type
func getFileExtension(mimeType string) string {
	switch mimeType {
	case "image/jpeg":
		return "jpg"
	case "image/png":
		return "png"
	case "image/gif":
		return "gif"
	case "image/webp":
		return "webp"
	case "image/svg+xml":
		return "svg"
	default:
		return "jpg" // Default to jpg
	}
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

