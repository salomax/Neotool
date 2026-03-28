import { gql } from "@apollo/client";

export const FUND_HOLDING_DETAIL = gql`
  query FundHoldingDetail($holdingId: ID!) {
    investmentHolding(id: $holdingId) {
      id
      quantity
      averageCost
      grossValue
      netValue
      valuationSource
      valuationReferenceDate
      isTaxExempt
      source
      financialAccount {
        id
        name
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
  }
`;
