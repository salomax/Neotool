package processor

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/domain"
)

// Processor handles the main business logic for institution enhancement
type Processor struct {
	cnpjaClient            domain.CNPJAClient
	repository             domain.ParentInstitutionRepository
	websiteProcessor       domain.WebsiteProcessor
	socialNetworkProcessor domain.SocialNetworkProcessor
	logoProcessor          *LogoProcessor
	logger                 *slog.Logger
}

// NewProcessor creates a new processor instance
func NewProcessor(
	cnpjaClient domain.CNPJAClient,
	repository domain.ParentInstitutionRepository,
	websiteProcessor domain.WebsiteProcessor,
	socialNetworkProcessor domain.SocialNetworkProcessor,
	logoProcessor *LogoProcessor,
	logger *slog.Logger,
) *Processor {
	return &Processor{
		cnpjaClient:            cnpjaClient,
		repository:             repository,
		websiteProcessor:       websiteProcessor,
		socialNetworkProcessor: socialNetworkProcessor,
		logoProcessor:          logoProcessor,
		logger:                 logger,
	}
}

// ProcessMessage processes an institution upserted message
func (p *Processor) ProcessMessage(ctx context.Context, msg *domain.InstitutionUpsertedMessage) error {
	p.logger.Info("Processing institution message",
		"bacen_cod_inst", msg.BacenCodInst,
		"change_type", msg.ChangeType,
		"event_id", msg.EventID)

	// Step 1: Construct parent CNPJ
	parentCNPJ, err := domain.ConstructParentCNPJ(msg.BacenCodInst, msg.CnpjInstituicaoLider)
	if err != nil {
		// If bacen_cod_inst starts with 'C', skip this message (no identifiable root CNPJ)
		p.logger.Warn("Skipping institution - cannot determine parent CNPJ",
			"bacen_cod_inst", msg.BacenCodInst,
			"cnpj_lider", msg.CnpjInstituicaoLider,
			"error", err.Error())
		return fmt.Errorf("failed to construct parent CNPJ: %w (non-retryable)", err)
	}

	p.logger.Info("Parent CNPJ determined",
		"parent_cnpj", parentCNPJ,
		"bacen_cod_inst", msg.BacenCodInst,
		"cnpj_lider", msg.CnpjInstituicaoLider)

	// Check enhancement cache: if financial_institution was updated within 24h, skip CNPJA and enrichment
	since := time.Now().Add(-24 * time.Hour)
	existing, err := p.repository.FindByParentCNPJUpdatedSince(ctx, parentCNPJ, since)
	if err != nil {
		return fmt.Errorf("failed to check enhancement cache: %w", err)
	}
	if existing != nil {
		p.logger.Info("Using existing financial_institution (updated within 24h), skipping CNPJA and enrichment",
			"parent_cnpj", parentCNPJ,
			"financial_institution_id", existing.ID,
			"bacen_cod_inst", msg.BacenCodInst)
		anoMesRef := ""
		if msg.AnoMesRef != nil && *msg.AnoMesRef != "" {
			anoMesRef = *msg.AnoMesRef
		} else {
			p.logger.Info("ano_mes_ref not provided in message, will link all available ano_mes_ref values",
				"bacen_cod_inst", msg.BacenCodInst,
				"parent_cnpj", parentCNPJ)
		}
		if err := p.repository.LinkAllBacenInstitutionsByParentCNPJ(ctx, *existing.ID, parentCNPJ, msg.BacenCodInst, anoMesRef); err != nil {
			return fmt.Errorf("failed to link bacen_institution records: %w", err)
		}
		p.logger.Info("Successfully processed institution message (cache hit)",
			"parent_cnpj", parentCNPJ,
			"financial_institution_id", existing.ID,
			"bacen_cod_inst", msg.BacenCodInst,
			"ano_mes_ref", msg.AnoMesRef,
			"event_id", msg.EventID)
		return nil
	}

	// Step 2: Query CNPJá API
	queryResult, err := p.cnpjaClient.Query(ctx, parentCNPJ)
	if err != nil {
		return fmt.Errorf("failed to query CNPJá API: %w", err)
	}

	// Step 3: Process query result
	var legalName, tradeName, emailDomain, website *string
	var socialNetworks map[string]string
	var receitaData map[string]interface{}

	if !queryResult.Found {
		// Institution not found in CNPJá (404) - store with null enhancement data
		p.logger.Info("Institution not found in CNPJá", "parent_cnpj", parentCNPJ)
	} else if queryResult.Error != nil {
		// API error (timeout, connection error, etc.)
		return fmt.Errorf("CNPJá API error: %w", queryResult.Error)
	} else {
		// Successfully retrieved data
		cnpjaResp := queryResult.Data

		// Extract legal name
		legalName = &cnpjaResp.Company.Name

		// Extract trade name (alias)
		tradeName = cnpjaResp.Alias

		// Extract email domain (filter blacklisted domains)
		emailDomain = ExtractDomainFromEmails(cnpjaResp.Emails)

		// Determine institution name for website search (priority: message name, trade name, legal name)
		institutionName := msg.NomeInstituicao
		nameSource := "message"
		if institutionName == nil || *institutionName == "" {
			if tradeName != nil && *tradeName != "" {
				institutionName = tradeName
				nameSource = "trade_name"
			} else if legalName != nil && *legalName != "" {
				institutionName = legalName
				nameSource = "legal_name"
			}
		}

		if institutionName != nil && *institutionName != "" {
			p.logger.Info("Using institution name for website search",
				"name", *institutionName,
				"source", nameSource)
		}

		// Extract and validate website
		// Lookup priority: bacenCodInst -> parentCNPJ -> cnpjInstituicaoLider (fallback)
		website = p.websiteProcessor.ExtractAndValidateWebsite(
			ctx,
			institutionName,
			msg.BacenCodInst,
			parentCNPJ,
			msg.CnpjInstituicaoLider,
			cnpjaResp,
			emailDomain,
		)

		// Scrape social networks (if website is available)
		if website != nil && *website != "" {
			socialNetworks = p.socialNetworkProcessor.ScrapeSocialNetworks(ctx, *website)
		}

		// Store full CNPJá response as receita_data
		receitaData = cnpjaResp.RawData

		p.logger.Info("Enhancement data extracted",
			"parent_cnpj", parentCNPJ,
			"legal_name", legalName,
			"trade_name", tradeName,
			"email_domain", emailDomain,
			"website", website,
			"social_networks_count", len(socialNetworks))
	}

	// Process logo (after website validation, even if CNPJá query failed)
	// Check if website changed - if so, we need to reprocess the logo
	var assetID *string
	if website != nil && *website != "" && p.logoProcessor != nil {
		// Check if institution already exists
		existingInstitution, err := p.repository.FindByParentCNPJ(ctx, parentCNPJ)

		websiteChanged := false
		if err == nil && existingInstitution != nil {
			// Check if website changed
			if existingInstitution.Website == nil || *existingInstitution.Website != *website {
				websiteChanged = true
				p.logger.Info("Website changed, will reprocess logo",
					"parent_cnpj", parentCNPJ,
					"old_website", existingInstitution.Website,
					"new_website", *website)
			}
		}

		if err == nil && existingInstitution != nil && existingInstitution.AssetID != nil && *existingInstitution.AssetID != "" && !websiteChanged {
			// Reuse existing asset_id only if website hasn't changed
			assetID = existingInstitution.AssetID
			p.logger.Info("Reusing existing logo asset_id (website unchanged)",
				"parent_cnpj", parentCNPJ,
				"website", website,
				"asset_id", *assetID)
		} else {
			// Process new logo: either no existing asset_id, or website changed
			assetID = p.logoProcessor.ProcessLogo(ctx, website)
			if assetID != nil {
				p.logger.Info("Logo processed successfully",
					"website", website,
					"asset_id", *assetID,
					"website_changed", websiteChanged)
			} else {
				p.logger.Info("Logo processing skipped or failed (non-fatal)",
					"website", website)
			}
		}
	}

	// Step 4: Create parent institution record
	now := time.Now()
		institution := &domain.ParentInstitution{
			ParentCNPJ:    parentCNPJ,
			LegalName:     legalName,
			TradeName:     tradeName,
			EmailDomain:   emailDomain,
			Website:       website,
			AssetID:       assetID,
			SocialNetwork: socialNetworks,
			ReceitaData:   receitaData,
			CreatedAt:     now,
			UpdatedAt:     now,
		}

	// Step 5: Upsert to database
	if err := p.repository.Upsert(ctx, institution); err != nil {
		return fmt.Errorf("failed to upsert parent institution: %w", err)
	}

	// Defensive check: Upsert should always set ID
	if institution.ID == nil {
		p.logger.Error("Upsert succeeded but ID is nil - this should not happen",
			"parent_cnpj", parentCNPJ)
		return fmt.Errorf("upsert succeeded but ID is nil")
	}

	// Step 6: Link ALL bacen_institution records that share the same parent_cnpj
	// This ensures that all institutions with the same cnpj_instituicao_lider
	// (or that would construct the same parent_cnpj) are linked to the same financial_institution
	anoMesRef := ""
	if msg.AnoMesRef != nil && *msg.AnoMesRef != "" {
		anoMesRef = *msg.AnoMesRef
	} else {
		p.logger.Info("ano_mes_ref not provided in message, will link all available ano_mes_ref values",
			"bacen_cod_inst", msg.BacenCodInst,
			"parent_cnpj", parentCNPJ)
	}
	if err := p.repository.LinkAllBacenInstitutionsByParentCNPJ(ctx, *institution.ID, parentCNPJ, msg.BacenCodInst, anoMesRef); err != nil {
		return fmt.Errorf("failed to link bacen_institution records: %w", err)
	}

	p.logger.Info("Successfully processed institution message",
		"parent_cnpj", parentCNPJ,
		"financial_institution_id", institution.ID,
		"bacen_cod_inst", msg.BacenCodInst,
		"ano_mes_ref", msg.AnoMesRef,
		"event_id", msg.EventID)

	return nil
}
