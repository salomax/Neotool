package domain

import (
	"encoding/json"
	"errors"
	"time"
)

var (
	// ErrMissingBacenCodInst is returned when bacen_cod_inst is missing
	ErrMissingBacenCodInst = errors.New("bacen_cod_inst is required")

	// ErrInvalidChangeType is returned when change_type is not CREATED or UPDATED
	ErrInvalidChangeType = errors.New("change_type must be CREATED or UPDATED")
)

// InstitutionUpsertedMessage represents a message from the financial-data.institution.upserted Kafka topic.
// Matches the schema defined in bacen_ifdata/schemas/institution_upserted.json
type InstitutionUpsertedMessage struct {
	SchemaVersion             string  `json:"schema_version"`
	EventID                   string  `json:"event_id"`
	OccurredAt                string  `json:"occurred_at"`
	Source                    string  `json:"source"`
	BacenCodInst              string  `json:"bacen_cod_inst"`
	ChangeType                string  `json:"change_type"`
	NomeInstituicao           *string `json:"nome_instituicao,omitempty"`
	Situacao                  *string `json:"situacao,omitempty"`
	SegmentoTb                *string `json:"segmento_tb,omitempty"`
	Atividade                 *string `json:"atividade,omitempty"`
	TipoConsolidadoBancario   *string `json:"tipo_consolidado_bancario,omitempty"`
	TipoConsolidacao          *string `json:"tipo_consolidacao,omitempty"`
	TipoControle              *string `json:"tipo_controle,omitempty"`
	UF                        *string `json:"uf,omitempty"`
	Municipio                 *string `json:"municipio,omitempty"`
	CodConglomeradoFinanceiro *string `json:"cod_conglomerado_financeiro,omitempty"`
	CodConglomeradoPrudencial *string `json:"cod_conglomerado_prudencial,omitempty"`
	CnpjInstituicaoLider      *string `json:"cnpj_instituicao_lider,omitempty"`
	SourceHash                *string `json:"source_hash,omitempty"`
	AnoMesRef                 *string `json:"ano_mes_ref,omitempty"`
}

// Validate validates the message fields.
func (m *InstitutionUpsertedMessage) Validate() error {
	if m.BacenCodInst == "" {
		return ErrMissingBacenCodInst
	}

	if m.ChangeType != "CREATED" && m.ChangeType != "UPDATED" {
		return ErrInvalidChangeType
	}

	return nil
}

// GetRecordID returns the record ID for idempotency (used by consumer).
func (m *InstitutionUpsertedMessage) GetRecordID() string {
	return m.BacenCodInst
}

// ParentInstitution represents enhancement data for a parent institution.
// Maps to the financial_data.financial_institution table.
type ParentInstitution struct {
	ID             *string                `json:"id,omitempty"` // UUID v7 primary key
	ParentCNPJ     string                 `json:"parent_cnpj"`
	LegalName      *string                `json:"legal_name,omitempty"`
	TradeName      *string                `json:"trade_name,omitempty"`
	EmailDomain    *string                `json:"email_domain,omitempty"`
	Website        *string                `json:"website,omitempty"`
	AssetID        *string                `json:"asset_id,omitempty"` // UUID string
	SocialNetwork  map[string]string      `json:"social_network,omitempty"`
	ReceitaData    map[string]interface{} `json:"receita_data,omitempty"`
	CreatedAt      time.Time              `json:"created_at"`
	UpdatedAt      time.Time              `json:"updated_at"`
}

// EmailField represents an email field from CNPJá API
type EmailField struct {
	Address string `json:"address"`
	Domain  string `json:"domain"`
}

// CNPJACompany represents the company field from CNPJá API response
type CNPJACompany struct {
	Name string `json:"name"`
}

// CNPJAResponse represents the response from CNPJá API
type CNPJAResponse struct {
	Alias   *string        `json:"alias"`
	Company CNPJACompany   `json:"company"`
	Emails  []EmailField   `json:"emails"`
	Phone   *string        `json:"phone,omitempty"`
	Address interface{}    `json:"address,omitempty"`

	// Website can be in multiple fields
	Website  *string `json:"website,omitempty"`
	Site     *string `json:"site,omitempty"`
	URL      *string `json:"url,omitempty"`
	Homepage *string `json:"homepage,omitempty"`
	Web      *string `json:"web,omitempty"`

	// Store full response for receita_data
	RawData map[string]interface{} `json:"-"`
}

// UnmarshalJSON custom unmarshaler to capture raw data
func (c *CNPJAResponse) UnmarshalJSON(data []byte) error {
	// First unmarshal into typed fields
	type Alias CNPJAResponse
	aux := &struct {
		*Alias
	}{
		Alias: (*Alias)(c),
	}
	if err := json.Unmarshal(data, &aux); err != nil {
		return err
	}

	// Then unmarshal into raw map for receita_data
	var rawMap map[string]interface{}
	if err := json.Unmarshal(data, &rawMap); err != nil {
		return err
	}
	c.RawData = rawMap

	return nil
}

// GetWebsite returns the website URL from any of the possible fields
func (c *CNPJAResponse) GetWebsite() *string {
	if c.Website != nil && *c.Website != "" {
		return c.Website
	}
	if c.Site != nil && *c.Site != "" {
		return c.Site
	}
	if c.URL != nil && *c.URL != "" {
		return c.URL
	}
	if c.Homepage != nil && *c.Homepage != "" {
		return c.Homepage
	}
	if c.Web != nil && *c.Web != "" {
		return c.Web
	}
	return nil
}
