package domain

import (
	"time"

	"github.com/google/uuid"
)

// ── B3 API response types ─────────────────────────────────────────────────────

// B3CompanyListResponse is the paginated response from GetInitialCompanies.
type B3CompanyListResponse struct {
	Page    B3Page      `json:"page"`
	Results []B3Company `json:"results"`
}

type B3Page struct {
	PageNumber   int `json:"pageNumber"`
	PageSize     int `json:"pageSize"`
	TotalRecords int `json:"totalRecords"`
	TotalPages   int `json:"totalPages"`
}

type B3Company struct {
	CodeCVM        string `json:"codeCVM"`
	IssuingCompany string `json:"issuingCompany"`
	CompanyName    string `json:"companyName"`
	TradingName    string `json:"tradingName"`
	CNPJ           string `json:"cnpj"`
	Status         string `json:"status"` // "A" = active
}

// B3SupplementItem is one element of the array returned by GetListedSupplementCompany.
type B3SupplementItem struct {
	Code           string            `json:"code"`
	CodeCVM        string            `json:"codeCVM"`
	CashDividends  []B3CashDividend  `json:"cashDividends"`
	StockDividends []B3StockDividend `json:"stockDividends"`
	Subscriptions  []B3Subscription  `json:"subscriptions"`
}

// B3CashDividend is one entry in the cashDividends array.
type B3CashDividend struct {
	AssetIssued   string `json:"assetIssued"`
	PaymentDate   string `json:"paymentDate"`   // DD/MM/YYYY
	Rate          string `json:"rate"`           // Brazilian decimal: "0,29642144000"
	RelatedTo     string `json:"relatedTo"`      // "Anual/2025"
	ApprovedOn    string `json:"approvedOn"`     // DD/MM/YYYY
	IsinCode      string `json:"isinCode"`
	Label         string `json:"label"`          // "DIVIDENDO" | "JRS CAP PROPRIO"
	LastDatePrior string `json:"lastDatePrior"`  // DD/MM/YYYY — data COM
	Remarks       string `json:"remarks"`
}

// B3StockDividend is one entry in the stockDividends array (splits, bonuses).
type B3StockDividend struct {
	AssetIssued   string `json:"assetIssued"`
	Factor        string `json:"factor"`         // Brazilian decimal: "100,00000000000"
	ApprovedOn    string `json:"approvedOn"`     // DD/MM/YYYY
	IsinCode      string `json:"isinCode"`
	Label         string `json:"label"`          // "DESDOBRAMENTO" | "GRUPAMENTO" | "BONIFICACAO"
	LastDatePrior string `json:"lastDatePrior"`  // DD/MM/YYYY — data COM
	Remarks       string `json:"remarks"`
}

// B3Subscription is one entry in the subscriptions array.
type B3Subscription struct {
	AssetIssued      string `json:"assetIssued"`
	Percentage       string `json:"percentage"`       // Brazilian decimal
	PriceUnit        string `json:"priceUnit"`         // Brazilian decimal
	TradingPeriod    string `json:"tradingPeriod"`     // "DD/MM/YYYY a DD/MM/YYYY"
	SubscriptionDate string `json:"subscriptionDate"`  // DD/MM/YYYY
	ApprovedOn       string `json:"approvedOn"`        // DD/MM/YYYY
	IsinCode         string `json:"isinCode"`
	Label            string `json:"label"`             // "SUBSCRICAO"
	LastDatePrior    string `json:"lastDatePrior"`     // DD/MM/YYYY — data COM
	Remarks          string `json:"remarks"`
}

// ── DB upsert parameter types ─────────────────────────────────────────────────

// CashDividendUpsertParam is one row destined for asset_cash_dividend.
type CashDividendUpsertParam struct {
	AssetID       *uuid.UUID // nil when isin_code has no match in investment_asset
	IsinCode      string
	DividendType  string    // DIVIDEND | JCP
	Rate          float64
	LastDatePrior time.Time // data COM
	PaymentDate   time.Time
	ApprovedOn    time.Time
	RelatedTo     string
	Remarks       string
	ContentHash   string
}

// StockEventUpsertParam is one row destined for asset_stock_event.
type StockEventUpsertParam struct {
	AssetID          *uuid.UUID
	IsinCode         string
	EventType        string     // SPLIT | REVERSE_SPLIT | BONUS_SHARES | SUBSCRIPTION
	Factor           *float64   // split ratio / bonus % / subscription %
	PriceUnit        *float64   // subscription price per share
	TradingStart     *time.Time // subscription: rights trading period start
	TradingEnd       *time.Time // subscription: rights trading period end
	SubscriptionDate *time.Time // subscription: exercise deadline
	LastDatePrior    time.Time  // data COM
	ApprovedOn       time.Time
	Remarks          string
	ContentHash      string
}
