package processor

import (
	"strings"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/domain"
)

// DomainBlacklist contains blacklisted email provider domains
var DomainBlacklist = map[string]bool{
	"gmail.com":      true,
	"hotmail.com":    true,
	"outlook.com":    true,
	"live.com":       true,
	"yahoo.com":      true,
	"icloud.com":     true,
	"bol.com.br":     true,
	"ig.com.br":      true,
	"terra.com.br":   true,
	"uol.com.br":     true,
	"globo.com":      true,
	"r7.com":         true,
	"superig.com.br": true,
	"zipmail.com.br": true,
	"tutanota.com":   true,
	"protonmail.com": true,
	"uai.com.br":     true,
}

// ExtractDomainFromEmails extracts domain from emails list in CNPJá response.
//
// Filters out blacklisted email provider domains (e.g., gmail.com, hotmail.com)
// and returns the first non-blacklisted domain found.
//
// Args:
//   emails: List of email fields from CNPJá API
//
// Returns:
//   Domain string, or nil if no valid (non-blacklisted) emails found
func ExtractDomainFromEmails(emails []domain.EmailField) *string {
	if len(emails) == 0 {
		return nil
	}

	// Iterate through emails to find first non-blacklisted domain
	for _, email := range emails {
		domain := strings.TrimSpace(email.Domain)
		if domain == "" {
			continue
		}

		// Check if domain is blacklisted (case-insensitive)
		if !DomainBlacklist[strings.ToLower(domain)] {
			return &domain
		}
	}

	// All domains were blacklisted or invalid
	return nil
}
