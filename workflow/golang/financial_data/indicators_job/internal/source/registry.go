package source

import (
	"fmt"
	"time"

	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
)

// SourceType represents external source type
type SourceType string

const (
	SourceSIDRA     SourceType = "SIDRA"
	SourceSGS       SourceType = "SGS"
	SourceCOMEXSTAT SourceType = "COMEXSTAT"
	SourceIPEADATA  SourceType = "IPEADATA"
	SourceFGV       SourceType = "FGV"
	SourceFIPE      SourceType = "FIPE"
)

// Request describes a fetch request for a source
type Request struct {
	Source         SourceType
	IndicatorLabel string
	VariationLabel string
	URL            string
	SeriesID       int
	SeriesCode     string
	MetadataURL    string
	Method         string
	Body           []byte
	StartDate      *time.Time
	EndDate        *time.Time
	CalcType       string
	LookbackMonths int
}

// Registry resolves indicator variations to source requests
type Registry struct {
	backfillMonths    int
	backfillYears     int
	incrementalMonths int
	incrementalYears  int
	incrementalDays   int
}

func NewRegistry(backfillMonths, backfillYears, incrementalMonths, incrementalYears, incrementalDays int) *Registry {
	return &Registry{
		backfillMonths:    backfillMonths,
		backfillYears:     backfillYears,
		incrementalMonths: incrementalMonths,
		incrementalYears:  incrementalYears,
		incrementalDays:   incrementalDays,
	}
}

// Resolve builds a source request for a variation
func (r *Registry) Resolve(v domain.IndicatorVariation, lastReference *time.Time) (*Request, error) {
	key := fmt.Sprintf("%s|%s", v.IndicatorLabel, v.Label)
	isIncremental := lastReference != nil && r.isIncrementalEligible(v, lastReference)

	switch key {
	case "IPCA|MONTHLY_CHANGE":
		return r.sidraRequest(1737, 63, r.monthsForRun(v, isIncremental)), nil
	case "IPCA|YTD":
		return r.sidraRequest(1737, 69, r.monthsForRun(v, isIncremental)), nil
	case "IPCA|ROLLING_12M":
		return r.sidraRequest(1737, 2265, r.monthsForRun(v, isIncremental)), nil
	case "IPCA_15|MONTHLY_CHANGE":
		return r.sidraRequest(3065, 355, r.monthsForRun(v, isIncremental)), nil
	case "IPCA_15|YTD":
		return r.sidraRequest(3065, 356, r.monthsForRun(v, isIncremental)), nil
	case "IPCA_15|ROLLING_12M":
		return r.sidraRequest(3065, 1120, r.monthsForRun(v, isIncremental)), nil
	case "INPC|MONTHLY_CHANGE":
		return r.sidraRequest(1736, 44, r.monthsForRun(v, isIncremental)), nil
	case "INPC|YTD":
		return r.sidraRequest(1736, 68, r.monthsForRun(v, isIncremental)), nil
	case "INPC|ROLLING_12M":
		return r.sidraRequest(1736, 2292, r.monthsForRun(v, isIncremental)), nil
	case "UNEMPLOYMENT|RATE":
		return r.sidraRequest(6381, 4099, r.monthsForRun(v, isIncremental)), nil
	case "AVERAGE_INCOME|HABITUAL":
		return r.sidraRequest(6390, 5933, r.monthsForRun(v, isIncremental)), nil
	case "AVERAGE_INCOME|EFFECTIVE":
		return r.sidraRequest(6387, 5935, r.monthsForRun(v, isIncremental)), nil
	case "GDP|VALUE":
		return r.sidraRequest(6784, 9808, r.yearsForRun(v, isIncremental)), nil
	case "SELIC|EFFECTIVE_RATE":
		return r.sgsRequest(11, v, lastReference, isIncremental), nil
	case "SELIC|TARGET_RATE":
		return r.sgsRequest(432, v, lastReference, isIncremental), nil
	case "CDI|RATE":
		return r.sgsRequest(12, v, lastReference, isIncremental), nil
	case "DI_OVER|RATE":
		return r.sgsRequest(12, v, lastReference, isIncremental), nil
	case "CURRENT_ACCOUNT|VALUE":
		return r.sgsRequest(22701, v, lastReference, isIncremental), nil
	case "INTERNATIONAL_RESERVES|VALUE":
		return r.sgsRequest(13621, v, lastReference, isIncremental), nil
	case "PUBLIC_DEBT_GDP|RATE":
		return r.sgsRequest(13762, v, lastReference, isIncremental), nil
	case "PRIMARY_RESULT|VALUE":
		return r.sgsRequest(4649, v, lastReference, isIncremental), nil
	case "TRADE_BALANCE|VALUE":
		return r.comexStatRequest(lastReference)
	case "IGP_M|MONTHLY_CHANGE":
		return r.ipeadataRequest("IGP12_IGPMG12", v, lastReference, isIncremental, "", 0), nil
	case "IGP_M|YTD":
		return r.ipeadataRequest("IGP12_IGPM12", v, lastReference, isIncremental, "YTD", 12), nil
	case "IGP_M|ROLLING_12M":
		return r.ipeadataRequest("IGP12_IGPM12", v, lastReference, isIncremental, "ROLLING_12M", 12), nil
	case "IGP_DI|MONTHLY_CHANGE":
		return r.ipeadataRequest("IGP12_IGPDIG12", v, lastReference, isIncremental, "", 0), nil
	case "IGP_DI|YTD":
		return r.ipeadataRequest("IGP12_IGPDI12", v, lastReference, isIncremental, "YTD", 12), nil
	case "IGP_DI|ROLLING_12M":
		return r.ipeadataRequest("IGP12_IGPDI12", v, lastReference, isIncremental, "ROLLING_12M", 12), nil
	default:
		return nil, fmt.Errorf("no source mapping for %s", key)
	}
}

func (r *Registry) sidraRequest(table, variable, lastN int) *Request {
	url := fmt.Sprintf("https://apisidra.ibge.gov.br/values/t/%d/n1/all/v/%d/p/last%%20%d", table, variable, lastN)
	return &Request{Source: SourceSIDRA, URL: url}
}

func (r *Registry) sgsRequest(seriesID int, v domain.IndicatorVariation, lastReference *time.Time, isIncremental bool) *Request {
	end := time.Now().UTC()
	start := end.AddDate(-r.backfillYears, 0, 0)
	if lastReference != nil {
		start = lastReference.AddDate(0, 0, 1)
	}
	if lastReference != nil && isIncremental {
		if days := r.incrementalDaysForRun(v, true); days > 0 {
			candidate := end.AddDate(0, 0, -days)
			if candidate.After(start) {
				start = candidate
			}
		}
	}
	url := fmt.Sprintf(
		"https://api.bcb.gov.br/dados/serie/bcdata.sgs.%d/dados?formato=json&dataInicial=%s&dataFinal=%s",
		seriesID,
		start.Format("02/01/2006"),
		end.Format("02/01/2006"),
	)
	metadataURL := fmt.Sprintf(
		"https://www3.bcb.gov.br/sgspub/consultarmetadados/consultarMetadadosSeries.do?method=consultarMetadadosSeriesInternet&hdOidSerieSelecionada=%d",
		seriesID,
	)
	return &Request{Source: SourceSGS, URL: url, SeriesID: seriesID, MetadataURL: metadataURL}
}

func (r *Registry) comexStatRequest(lastReference *time.Time) (*Request, error) {
	end := time.Now().UTC()
	start := end.AddDate(-r.backfillYears, 0, 0)
	if lastReference != nil {
		start = lastReference.AddDate(0, 1, 0)
	}
	if start.After(end) {
		start = end
	}

	body := fmt.Sprintf(`{
  "flow": "balance",
  "monthDetail": true,
  "period": { "from": "%s", "to": "%s" },
  "filters": [],
  "details": [],
  "metrics": ["metricFOB"]
}`, start.Format("2006-01"), end.Format("2006-01"))

	return &Request{
		Source: SourceCOMEXSTAT,
		URL:    "https://api-comexstat.mdic.gov.br/general?language=pt",
		Method: "POST",
		Body:   []byte(body),
	}, nil
}

func (r *Registry) ipeadataRequest(seriesCode string, v domain.IndicatorVariation, lastReference *time.Time, isIncremental bool, calcType string, lookbackMonths int) *Request {
	end := time.Now().UTC()
	start := end.AddDate(-r.backfillYears, 0, 0)
	if lastReference != nil {
		start = lastReference.AddDate(0, 0, 1)
	}
	if lastReference != nil && isIncremental {
		months := r.monthsForRun(v, true)
		candidate := end.AddDate(0, -months, 0)
		if candidate.After(start) {
			start = candidate
		}
	}
	if lookbackMonths > 0 {
		lookbackStart := start.AddDate(0, -lookbackMonths, 0)
		if lookbackStart.Before(start) {
			start = lookbackStart
		}
	}
	return &Request{
		Source:         SourceIPEADATA,
		SeriesCode:     seriesCode,
		StartDate:      &start,
		EndDate:        &end,
		CalcType:       calcType,
		LookbackMonths: lookbackMonths,
	}
}

func (r *Registry) monthsForRun(v domain.IndicatorVariation, isIncremental bool) int {
	if isIncremental && r.incrementalMonths > 0 {
		if v.IncrementalMonths != nil && *v.IncrementalMonths > 0 {
			return *v.IncrementalMonths
		}
		return r.incrementalMonths
	}
	return r.backfillMonths
}

func (r *Registry) yearsForRun(v domain.IndicatorVariation, isIncremental bool) int {
	if isIncremental && r.incrementalYears > 0 {
		if v.IncrementalYears != nil && *v.IncrementalYears > 0 {
			return *v.IncrementalYears
		}
		return r.incrementalYears
	}
	return r.backfillYears
}

func (r *Registry) incrementalDaysForRun(v domain.IndicatorVariation, isIncremental bool) int {
	if v.IncrementalDays != nil && *v.IncrementalDays > 0 {
		return *v.IncrementalDays
	}
	if isIncremental {
		return r.incrementalDays
	}
	return 0
}

func (r *Registry) isIncrementalEligible(v domain.IndicatorVariation, lastReference *time.Time) bool {
	if lastReference == nil {
		return false
	}
	now := time.Now().UTC()
	switch v.IndicatorFrequency {
	case "daily":
		days := r.incrementalDaysForRun(v, true)
		if days <= 0 {
			return true
		}
		return lastReference.After(now.AddDate(0, 0, -days))
	case "annual":
		years := r.yearsForRun(v, true)
		return lastReference.After(now.AddDate(-years, 0, 0))
	default: // monthly/weekly/quarterly
		months := r.monthsForRun(v, true)
		return lastReference.After(now.AddDate(0, -months, 0))
	}
}
