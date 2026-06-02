package domain

const (
	SourceB3 = "B3"

	JobName = "b3_corporate_events"

	// Cash dividend types (stored in asset_cash_dividend.dividend_type)
	DividendTypeDividend = "DIVIDEND"
	DividendTypeJCP      = "JCP"

	// Stock event types (stored in asset_stock_event.event_type)
	EventTypeSplit        = "SPLIT"
	EventTypeReverseSplit = "REVERSE_SPLIT"
	EventTypeBonusShares  = "BONUS_SHARES"
	EventTypeSubscription = "SUBSCRIPTION"
)

// B3LabelToDividendType maps B3 API label values to our canonical dividend types.
// Labels are matched case-insensitively after trimming.
var B3LabelToDividendType = map[string]string{
	"DIVIDENDO":       DividendTypeDividend,
	"JRS CAP PROPRIO": DividendTypeJCP,
}

// B3LabelToEventType maps B3 API label values to our canonical event types.
var B3LabelToEventType = map[string]string{
	"DESDOBRAMENTO": EventTypeSplit,
	"GRUPAMENTO":    EventTypeReverseSplit,
	"BONIFICACAO":   EventTypeBonusShares,
	"BONIFICAÇÃO":   EventTypeBonusShares,
	"SUBSCRICAO":    EventTypeSubscription,
	"SUBSCRIÇÃO":    EventTypeSubscription,
}
