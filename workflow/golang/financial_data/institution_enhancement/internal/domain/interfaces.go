package domain

import "context"

// ParentInstitutionRepository defines the interface for parent institution persistence operations
type ParentInstitutionRepository interface {
	// Upsert inserts or updates a parent institution record
	Upsert(ctx context.Context, institution *ParentInstitution) error

	// FindByParentCNPJ retrieves a parent institution by CNPJ
	FindByParentCNPJ(ctx context.Context, parentCNPJ string) (*ParentInstitution, error)

	// Exists checks if a parent institution exists
	Exists(ctx context.Context, parentCNPJ string) (bool, error)

	// LinkBacenInstitution links a bacen_institution to a financial_institution
	// Updates both the bacen_institution.financial_institution_id column and the relation table
	LinkBacenInstitution(ctx context.Context, financialInstitutionID string, bacenCodInst string, anoMesRef string) error

	// LinkAllBacenInstitutionsByParentCNPJ links all bacen_institution records
	// that would construct the same parent_cnpj to the given financial_institution.
	// This ensures that all institutions with the same cnpj_instituicao_lider
	// (or that would construct the same parent_cnpj) are linked to the same financial_institution.
	// messageBacenCodInst and messageAnoMesRef are the values from the Kafka message for the specific record being processed.
	LinkAllBacenInstitutionsByParentCNPJ(ctx context.Context, financialInstitutionID string, parentCNPJ string, messageBacenCodInst string, messageAnoMesRef string) error
}

// CNPJAClient defines the interface for CNPJá API interactions
type CNPJAClient interface {
	// Query queries the CNPJá API for company information by CNPJ
	Query(ctx context.Context, cnpj string) (*CNPJAQueryResult, error)
}

// CNPJAQueryResult represents the result of a CNPJá API query
type CNPJAQueryResult struct {
	Found bool          // True if company found, false if 404
	Error error         // Error if request failed
	Data  *CNPJAResponse // Company data if found
}

// WebsiteProcessor defines the interface for website extraction and validation
type WebsiteProcessor interface {
	// ExtractAndValidateWebsite extracts and validates a website URL from multiple sources
	ExtractAndValidateWebsite(
		ctx context.Context,
		institutionName *string,
		cnpj string,
		cnpjaResp *CNPJAResponse,
		emailDomain *string,
	) *string
}

// SocialNetworkProcessor defines the interface for social network scraping
type SocialNetworkProcessor interface {
	// ScrapeSocialNetworks scrapes social network links from a website
	ScrapeSocialNetworks(ctx context.Context, website string) map[string]string
}

// WebsiteCSVLookup defines the interface for looking up websites from CSV data
type WebsiteCSVLookup interface {
	// LookupWebsite looks up a website by CNPJ
	// Returns the website URL and true if found, empty string and false otherwise
	LookupWebsite(cnpj string) (string, bool)
}

// MessageProcessor defines the interface for processing institution messages
type MessageProcessor interface {
	// ProcessMessage processes an institution upserted message
	ProcessMessage(ctx context.Context, msg *InstitutionUpsertedMessage) error
}

// DLQPublisher defines the interface for publishing failed messages to Dead Letter Queue
type DLQPublisher interface {
	// Publish publishes a failed message to the DLQ with metadata
	Publish(
		originalMessage []byte,
		originalKey string,
		originalTopic string,
		err error,
		retryCount int,
		isRetryable bool,
	) error

	// Close closes the DLQ publisher
	Close() error
}
