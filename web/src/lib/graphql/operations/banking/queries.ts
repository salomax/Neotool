import { gql } from '@apollo/client';

/** All financial accounts for the authenticated user's account. */
export const FINANCIAL_ACCOUNTS = gql`
  query FinancialAccounts {
    financialAccounts {
      id
      type
      subtype
      name
      currencyCode
      includeInNetWorth
      includeInDashboard
      themeColor
      issuerName
      issuer {
        id
        name
        domain
        countryCode
        logoKey
        logoAsset {
          id
        }
      }
      bankData {
        branchCode
        number
        checkDigit
        maskedNumber
      }
      cardData {
        maskedNumber
        bin
        cardNetwork
        cardLevel
        creditLimit
        availableCreditLimit
        minimumPaymentPercentage
        closingDay
        dueDay
      }
      connectionStatus
      consentExpiresAt
      lastSyncAt
      createdAt
      updatedAt
    }
  }
`;

/** All active issuers. */
export const ISSUERS = gql`
  query Issuers {
    issuers {
      id
      name
      compeCode
      logoAsset {
        id
        publicUrl
      }
    }
  }
`;
