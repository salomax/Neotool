package processor

import (
	"context"
	"log/slog"
	"strings"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/domain"
	httpClient "github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/http"
	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/serper"
)

// SpecialWebsites maps institution name keywords to their trusted websites
// These websites are trusted and don't need validation
var SpecialWebsites = map[string]string{
	"sicoob":  "https://www.sicoob.com.br",
	"cresol":  "https://cresol.com.br",
	"sicredi": "https://sicredi.com.br",
	"unicred": "https://www.unicred.com.br/",
}

// WebsiteProcessor handles website extraction and validation
type WebsiteProcessor struct {
	httpClient   *httpClient.Client
	serperClient *serper.Client
	csvLookup    domain.WebsiteCSVLookup
	logger       *slog.Logger
}

// NewWebsiteProcessor creates a new website processor
func NewWebsiteProcessor(httpClient *httpClient.Client, serperClient *serper.Client, csvLookup domain.WebsiteCSVLookup, logger *slog.Logger) *WebsiteProcessor {
	return &WebsiteProcessor{
		httpClient:   httpClient,
		serperClient: serperClient,
		csvLookup:    csvLookup,
		logger:       logger,
	}
}

// ExtractAndValidateWebsite extracts and validates website URL
//
// Priority order:
// 1. Check for special institution keywords (SICOOB, CRESOL, etc.) - no validation needed
// 2. Lookup in CSV file (if enabled)
// 3. Extract from CNPJá API response (website, site, url, homepage, web fields)
// 4. Construct from email domain if available
// 5. Search using Serper API (if enabled)
//
// Validation (only for non-special websites):
// - First attempt: Add "www." prefix if not present (HTTP client retries: 2 attempts with 30s, 60s backoff)
// - Second attempt: Remove "www." prefix if first attempt failed
// - Accept 2xx and 3xx status codes as "reachable"
// - Timeout: 20 seconds per request
func (wp *WebsiteProcessor) ExtractAndValidateWebsite(
	ctx context.Context,
	institutionName *string,
	cnpj string,
	cnpjaResp *domain.CNPJAResponse,
	emailDomain *string,
) *string {
	wp.logger.Info("Starting website extraction",
		"has_institution_name", institutionName != nil,
		"has_cnpja_response", cnpjaResp != nil,
		"has_email_domain", emailDomain != nil)

	// Priority 1: Check for special institution keywords
	if institutionName != nil && *institutionName != "" {
		wp.logger.Info("Checking for special institution website", "institution_name", *institutionName)
		specialWebsite := wp.checkSpecialWebsite(*institutionName)
		if specialWebsite != nil {
			wp.logger.Info("Using special website (no validation needed)",
				"institution_name", *institutionName,
				"website", *specialWebsite)
			return specialWebsite
		}
		wp.logger.Info("No special website found for institution", "institution_name", *institutionName)
	}

	// Priority 2: Lookup in CSV file (if enabled)
	if wp.csvLookup != nil && cnpj != "" {
		wp.logger.Info("Attempting to lookup website in CSV file", "cnpj", cnpj)
		website, found := wp.csvLookup.LookupWebsite(cnpj)
		if found && website != "" {
			wp.logger.Info("Website found in CSV file",
				"url", website,
				"cnpj", cnpj)

			// Validate the website from CSV
			normalized := normalizeURL(website)
			if validatedURL := wp.validateWebsite(ctx, normalized); validatedURL != nil {
				wp.logger.Info("Website from CSV validated successfully", "url", *validatedURL)
				return validatedURL
			}
			wp.logger.Warn("Website from CSV failed validation", "url", normalized)
		} else {
			wp.logger.Info("No website found in CSV file for CNPJ", "cnpj", cnpj)
		}
	} else {
		if wp.csvLookup == nil {
			wp.logger.Info("CSV lookup not enabled (lookup is nil)")
		} else {
			wp.logger.Info("CSV lookup skipped (no CNPJ provided)")
		}
	}

	// Priority 3: Extract from CNPJá API response
	if cnpjaResp != nil {
		apiWebsite := cnpjaResp.GetWebsite()
		wp.logger.Info("Extracting website from CNPJá API",
			"has_website", apiWebsite != nil,
			"raw_website", apiWebsite)

		if apiWebsite != nil && *apiWebsite != "" {
			normalized := normalizeURL(*apiWebsite)
			wp.logger.Info("Normalized website from CNPJá API",
				"original", *apiWebsite,
				"normalized", normalized)

			if validatedURL := wp.validateWebsite(ctx, normalized); validatedURL != nil {
				wp.logger.Info("Website validated from CNPJá API", "url", *validatedURL)
				return validatedURL
			}
			wp.logger.Warn("Website from CNPJá API failed validation", "url", normalized)
		} else {
			wp.logger.Info("No website found in CNPJá API response")
		}
	}

	// Priority 4: Construct from email domain
	if emailDomain != nil && *emailDomain != "" {
		wp.logger.Info("Attempting to construct website from email domain", "email_domain", *emailDomain)
		domainWebsite := normalizeURL(*emailDomain)
		wp.logger.Info("Normalized email domain to URL", "original", *emailDomain, "normalized", domainWebsite)

		if validatedURL := wp.validateWebsite(ctx, domainWebsite); validatedURL != nil {
			wp.logger.Info("Website validated from email domain", "url", *validatedURL)
			return validatedURL
		}
		wp.logger.Warn("Website from email domain failed validation", "url", domainWebsite)
	} else {
		wp.logger.Info("No email domain available for website construction")
	}

	// Priority 5: Search using Serper API (if enabled)
	if wp.serperClient != nil && institutionName != nil && *institutionName != "" {
		wp.logger.Info("Attempting to find website using Serper API",
			"institution_name", *institutionName,
			"cnpj", cnpj)

		website, err := wp.serperClient.SearchWebsite(ctx, *institutionName, cnpj)
		if err != nil {
			wp.logger.Warn("Serper API search failed",
				"institution_name", *institutionName,
				"error", err.Error())
		} else if website != "" {
			wp.logger.Info("Website found using Serper API",
				"url", website,
				"institution_name", *institutionName)

			// Validate the website found by Serper
			if validatedURL := wp.validateWebsite(ctx, website); validatedURL != nil {
				wp.logger.Info("Website from Serper validated successfully", "url", *validatedURL)
				return validatedURL
			}
			wp.logger.Warn("Website from Serper failed validation", "url", website)
		} else {
			wp.logger.Info("No website found using Serper API")
		}
	} else {
		if wp.serperClient == nil {
			wp.logger.Info("Serper API not enabled (client is nil)")
		} else {
			wp.logger.Info("Serper API skipped (no institution name)")
		}
	}

	// No valid website found
	wp.logger.Warn("No valid website found after all attempts")
	return nil
}

// checkSpecialWebsite checks if institution name contains special keywords
func (wp *WebsiteProcessor) checkSpecialWebsite(institutionName string) *string {
	nameLower := strings.ToLower(institutionName)

	for keyword, website := range SpecialWebsites {
		if strings.Contains(nameLower, keyword) {
			return &website
		}
	}

	return nil
}

// validateWebsite validates that a website is reachable
//
// Note: Retry logic is handled by the HTTP client (2 attempts: 30s, 60s backoff)
// First attempt: adds "www." prefix if not present
// Second attempt: removes "www." prefix if first attempt failed
// Returns the validated URL or nil if unreachable
func (wp *WebsiteProcessor) validateWebsite(ctx context.Context, website string) *string {
	wp.logger.Info("Starting website validation", "url", website)

	// First attempt: try with www. prefix (if not already present)
	var firstAttemptURL string
	if !hasWWWPrefix(website) {
		firstAttemptURL = addWWWPrefix(website)
		wp.logger.Info("First attempt: trying with www prefix",
			"original_url", website,
			"attempt_url", firstAttemptURL)
	} else {
		firstAttemptURL = website
		wp.logger.Info("First attempt: URL already has www prefix",
			"attempt_url", firstAttemptURL)
	}

	reachable, err := wp.httpClient.IsReachable(ctx, firstAttemptURL)

	if err != nil {
		wp.logger.Warn("First attempt failed",
			"url", firstAttemptURL,
			"error", err.Error(),
			"reachable", reachable)
	} else if reachable {
		wp.logger.Info("Website validated successfully on first attempt", "url", firstAttemptURL)
		return &firstAttemptURL
	} else {
		wp.logger.Warn("Website not reachable on first attempt",
			"url", firstAttemptURL,
			"reachable", reachable)
	}

	// Second attempt: try without www. prefix (if first attempt had it)
	if hasWWWPrefix(firstAttemptURL) {
		websiteWithoutWWW := removeWWWPrefix(firstAttemptURL)
		wp.logger.Info("Second attempt: trying without www prefix",
			"original_url", firstAttemptURL,
			"attempt_url", websiteWithoutWWW)

		reachable, err = wp.httpClient.IsReachable(ctx, websiteWithoutWWW)

		if err != nil {
			wp.logger.Warn("Second attempt failed",
				"url", websiteWithoutWWW,
				"error", err.Error(),
				"reachable", reachable)
		} else if reachable {
			wp.logger.Info("Website validated successfully on second attempt", "url", websiteWithoutWWW)
			return &websiteWithoutWWW
		} else {
			wp.logger.Warn("Website not reachable on second attempt",
				"url", websiteWithoutWWW,
				"reachable", reachable)
		}
	} else {
		wp.logger.Info("Skipping second attempt (first attempt didn't have www)", "url", firstAttemptURL)
	}

	wp.logger.Warn("All website validation attempts failed",
		"original_url", website,
		"last_error", err)
	return nil
}

// hasWWWPrefix checks if URL already has www. subdomain
func hasWWWPrefix(url string) bool {
	// Remove protocol
	withoutProtocol := url
	if idx := strings.Index(url, "://"); idx != -1 {
		withoutProtocol = url[idx+3:]
	}

	return strings.HasPrefix(withoutProtocol, "www.")
}

// addWWWPrefix adds www. to the URL if it doesn't have it
func addWWWPrefix(url string) string {
	// Extract protocol
	protocol := "https://"
	domain := url

	if idx := strings.Index(url, "://"); idx != -1 {
		protocol = url[:idx+3]
		domain = url[idx+3:]
	}

	// Add www. to domain
	return protocol + "www." + domain
}

// removeWWWPrefix removes www. from the URL if it has it
func removeWWWPrefix(url string) string {
	// Extract protocol
	protocol := "https://"
	domain := url

	if idx := strings.Index(url, "://"); idx != -1 {
		protocol = url[:idx+3]
		domain = url[idx+3:]
	}

	// Remove www. from domain if present
	if strings.HasPrefix(domain, "www.") {
		domain = domain[4:]
	}

	return protocol + domain
}

// normalizeURL ensures URL has https:// protocol
func normalizeURL(url string) string {
	url = strings.TrimSpace(url)
	if url == "" {
		return url
	}

	if !strings.HasPrefix(url, "http://") && !strings.HasPrefix(url, "https://") {
		return "https://" + url
	}

	return url
}
