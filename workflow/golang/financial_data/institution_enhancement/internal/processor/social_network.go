package processor

import (
	"context"
	"log/slog"

	httpClient "github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/http"
)

// SocialNetworkProcessor handles social network scraping
type SocialNetworkProcessor struct {
	scraper *httpClient.Scraper
	logger  *slog.Logger
}

// NewSocialNetworkProcessor creates a new social network processor
func NewSocialNetworkProcessor(scraper *httpClient.Scraper, logger *slog.Logger) *SocialNetworkProcessor {
	return &SocialNetworkProcessor{
		scraper: scraper,
		logger:  logger,
	}
}

// ScrapeSocialNetworks scrapes social network links from a website
//
// Graceful degradation: If scraping fails, returns empty map (doesn't block processing)
//
// Supported networks:
// - Facebook (facebook.com, fb.com)
// - Instagram (instagram.com)
// - LinkedIn (linkedin.com)
// - Twitter/X (twitter.com, x.com)
// - YouTube (youtube.com, youtu.be)
// - WhatsApp (whatsapp.com, wa.me)
func (snp *SocialNetworkProcessor) ScrapeSocialNetworks(ctx context.Context, website string) map[string]string {
	if website == "" {
		return map[string]string{}
	}

	snp.logger.Info("Scraping social networks", "website", website)

	socialNetworks, err := snp.scraper.ScrapeSocialNetworks(ctx, website)
	if err != nil {
		snp.logger.Warn("Social network scraping failed (continuing with empty map)",
			"website", website,
			"error", err)
		return map[string]string{}
	}

	snp.logger.Info("Social network scraping complete",
		"website", website,
		"networks_found", len(socialNetworks))

	return socialNetworks
}
