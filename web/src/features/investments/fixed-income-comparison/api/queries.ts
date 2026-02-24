import { gql } from "@apollo/client";

export const ECONOMIC_INDICATORS_LATEST = gql`
  query EconomicIndicatorsLatest(
    $indicatorLabel: String
    $variationLabel: String
  ) {
    economicIndicators(label: $indicatorLabel) {
      label
      variations(label: $variationLabel) {
        label
        series(limit: 1) {
          unit
          values {
            referenceDate
            value
          }
        }
      }
    }
  }
`;

export const FIXED_INCOME_COMPARISON = gql`
  query FixedIncomeComparison($input: FixedIncomeComparisonInput!) {
    fixedIncomeComparison(input: $input) {
      summary {
        baseProduct
        baseProductGroup
        baseRateModel
        baseEquivalentRateBps
        baseRateDisplay
        principal
        gross
        ir
        net
        realGainPercent
      }
      rows {
        label
        productGroup
        rateModel
        equivalentRateBps
        contractedRateDisplay
        gross
        ir
        net
        realGainPercent
        isExempt
        isBase
      }
      performanceSeries {
        productGroup
        points {
          date
          netValue
        }
      }
      methodology {
        dayCount {
          accrualConvention
          calendarDays
          businessDays
        }
        tax {
          appliesOnProfitOnly
          activeBracket {
            fromCalendarDays
            toCalendarDays
            rateDecimal
          }
          bracketTable {
            fromCalendarDays
            toCalendarDays
            rateDecimal
          }
        }
      }
    }
  }
`;
