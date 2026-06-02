"use client";

import { gql } from "@apollo/client";

export const FIXED_INCOME_HOLDING_DETAIL = gql`
  query FixedIncomeHoldingDetail($holdingId: ID!) {
    investmentHolding(id: $holdingId) {
      id
      quantity
      averageCost
      grossValue
      netValue
      incomeTax
      iof
      accruedInterest
      valuationSource
      valuationReferenceDate
      lastValuedAt
      issueDate
      gracePeriodDate
      maturityDate
      isExpired
      isTaxExempt
      indexer
      indexerPercentage
      fixedAnnualRate
      couponFrequency
      couponDayOfMonth
      couponSchedule {
        seriesNumber
        scheduledDate
        periodStart
        periodEnd
        isPartialPeriod
        isPast
        matchedTransaction {
          id
          tradeDate
          grossValue
          netValue
          incomeTax
          iof
        }
      }
      source
      financialAccount {
        id
        name
        currencyCode
        issuerName
        issuer {
          id
          name
          logoAsset {
            id
            publicUrl
          }
        }
      }
      asset {
        id
        name
        assetType
        issuerName
        currencyCode
        logoAsset {
          id
          publicUrl
        }
      }
    }
    investmentTransactions(holdingId: $holdingId) {
      id
      type
      direction
      quantity
      unitPrice
      grossValue
      netValue
      incomeTax
      iof
      indexerPercentage
      fixedAnnualRate
      tradeDate
      settlementDate
      source
    }
    holdingPerformance(holdingId: $holdingId, resolution: MONTH, includeProjection: true) {
      holdingId
      assetType
      currencyCode
      resolution
      valuationSource
      valuationReferenceDate
      capabilities {
        supportsProjection
        projectionDefaultEnabled
        supportedResolutions
        defaultResolution
      }
      summary {
        investedPrincipal
        netContributions
        currentCostBasis
        grossValue
        netValue
        incomeTax
        iof
        grossGain
        netGain
        grossGainPercent
      }
      points {
        timestamp
        isProjected
        investedPrincipal
        netContributions
        currentCostBasis
        grossValue
        netValue
        incomeTax
        iof
        grossGain
        netGain
        quantity
      }
    }
    investmentHoldingPerformance(holdingId: $holdingId) {
      holdingId
      xirr
      xirrSince
      totalReturn
      totalReturnAbsolute
      dividendsReceived
    }
  }
`;
