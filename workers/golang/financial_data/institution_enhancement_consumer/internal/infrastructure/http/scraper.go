package http

import (
	"context"
	"log/slog"
	"strings"

	"github.com/PuerkitoBio/goquery"
)

// Scraper scrapes HTML content for specific information
type Scraper struct {
	httpClient *Client
	logger     *slog.Logger
}

// NewScraper creates a new HTML scraper
func NewScraper(httpClient *Client, logger *slog.Logger) *Scraper {
	return &Scraper{
		httpClient: httpClient,
		logger:     logger,
	}
}

// ScrapeSocialNetworks scrapes social network links from a website
func (s *Scraper) ScrapeSocialNetworks(ctx context.Context, websiteURL string) (map[string]string, error) {
	s.logger.Info("Scraping social networks", "url", websiteURL)

	// Fetch HTML
	html, err := s.httpClient.FetchHTML(ctx, websiteURL)
	if err != nil {
		s.logger.Warn("Failed to fetch HTML for social network scraping", "url", websiteURL, "error", err)
		return map[string]string{}, nil // Return empty map, don't fail processing
	}

	// Parse HTML
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		s.logger.Warn("Failed to parse HTML", "url", websiteURL, "error", err)
		return map[string]string{}, nil
	}

	socialNetworks := make(map[string]string)

	// Find all links
	doc.Find("a[href]").Each(func(i int, sel *goquery.Selection) {
		href, exists := sel.Attr("href")
		if !exists || href == "" {
			return
		}

		href = strings.ToLower(href)

		// Facebook
		if (strings.Contains(href, "facebook.com") || strings.Contains(href, "fb.com")) &&
			!strings.Contains(href, "sharer") {
			if _, exists := socialNetworks["facebook"]; !exists {
				socialNetworks["facebook"] = extractFullURL(href)
			}
		}

		// Instagram
		if strings.Contains(href, "instagram.com") {
			if _, exists := socialNetworks["instagram"]; !exists {
				socialNetworks["instagram"] = extractFullURL(href)
			}
		}

		// LinkedIn
		if strings.Contains(href, "linkedin.com") {
			if _, exists := socialNetworks["linkedin"]; !exists {
				socialNetworks["linkedin"] = extractFullURL(href)
			}
		}

		// Twitter/X
		if strings.Contains(href, "twitter.com") || strings.Contains(href, "x.com") {
			if _, exists := socialNetworks["twitter"]; !exists {
				socialNetworks["twitter"] = extractFullURL(href)
			}
		}

		// YouTube
		if strings.Contains(href, "youtube.com") || strings.Contains(href, "youtu.be") {
			if _, exists := socialNetworks["youtube"]; !exists {
				socialNetworks["youtube"] = extractFullURL(href)
			}
		}

		// WhatsApp
		if strings.Contains(href, "whatsapp.com") || strings.Contains(href, "wa.me") {
			if _, exists := socialNetworks["whatsapp"]; !exists {
				socialNetworks["whatsapp"] = extractFullURL(href)
			}
		}
	})

	s.logger.Info("Social network scraping complete",
		"url", websiteURL,
		"networks_found", len(socialNetworks),
		"networks", socialNetworks)

	return socialNetworks, nil
}

// extractFullURL ensures URL has http/https prefix
func extractFullURL(href string) string {
	href = strings.TrimSpace(href)

	// If already has protocol, return as-is
	if strings.HasPrefix(href, "http://") || strings.HasPrefix(href, "https://") {
		return href
	}

	// Add https:// prefix
	if strings.HasPrefix(href, "//") {
		return "https:" + href
	}

	return "https://" + href
}
