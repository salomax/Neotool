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
          publicUrl
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
  query Issuers($categories: [IssuerCategory!]) {
    issuers(categories: $categories) {
      id
      name
      compeCode
      category
      logoAsset {
        id
        publicUrl
      }
    }
  }
`;

/** Transactions with pagination and filters. */
export const TRANSACTIONS = gql`
  query Transactions(
    $financialAccountId: ID
    $billId: ID
    $month: Int
    $year: Int
    $first: Int
    $after: String
  ) {
    transactions(
      financialAccountId: $financialAccountId
      billId: $billId
      month: $month
      year: $year
      first: $first
      after: $after
    ) {
      edges {
        node {
          id
          description
          category {
            id
            name
            color
            icon
          }
          type
          amount
          creditDebitType
          tags
          currencyCode
          date
          status
          financialAccountId
          billId
          installmentNumber
          totalInstallments
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;

/** All transaction categories (system + account-scoped). */
export const TRANSACTION_CATEGORIES = gql`
  query TransactionCategories {
    transactionCategories {
      id
      parentId
      name
      color
      icon
      isSystem
      applicableTo
      sortOrder
    }
  }
`;

/** Get all unique transaction tags. */
export const TRANSACTION_TAGS = gql`
  query TransactionTags {
    transactionTags {
      tag
    }
  }
`;

/** Single transaction import job by ID (includes duplicate candidates for review). */
export const TRANSACTION_IMPORT = gql`
  query TransactionImport($id: ID!) {
    transactionImport(id: $id) {
      id
      status
      errorDetails
      importedCount
      skippedCount
      duplicateReviewCount
      errorCount
      duplicateCandidates {
        rowIndex
        parsed {
          creditDebitType
          amount
          date
          description
          currencyCode
          counterpartyName
          counterpartyDocument
        }
        candidates {
          id
          date
          description
          amount
          creditDebitType
          currencyCode
          source
        }
      }
    }
  }
`;

/** List transaction import jobs (e.g. to show REVIEW_REQUIRED on page load). */
export const TRANSACTION_IMPORTS = gql`
  query TransactionImports($financialAccountId: ID) {
    transactionImports(financialAccountId: $financialAccountId) {
      id
      status
      duplicateReviewCount
    }
  }
`;

/** Month summary for financial transactions. */
export const MONTH_SUMMARY = gql`
  query MonthSummary(
    $financialAccountId: ID
    $year: Int
    $month: Int
  ) {
    monthSummary(
      financialAccountId: $financialAccountId
      year: $year
      month: $month
    ) {
      year
      month
      totalIncome
      totalExpenses
      closingBalance
      openingBalance
      transfersIn
      transfersOut
      projectedClosingBalance
      isCurrentMonth
      isFutureMonth
      expensesByCategory {
        category {
          id
          name
          icon
          color
        }
        totalAmount
        transactionCount
        percentage
      }
      incomeByCategory {
        category {
          id
          name
          icon
          color
        }
        totalAmount
        transactionCount
        percentage
      }
    }
  }
`;

/** All credit card bills (faturas) for a given card account, ordered by closing date desc. */
export const CREDIT_CARD_BILLS = gql`
  query GetCreditCardBills($financialAccountId: ID!) {
    creditCardBills(financialAccountId: $financialAccountId) {
      id
      financialAccountId
      closingDate
      dueDate
      totalAmount
      minimumPayment
      status
      createdAt
      updatedAt
    }
  }
`;

/** A specific credit card bill by financial account and optional closing date. */
export const CREDIT_CARD_BILL = gql`
  query GetCreditCardBill($financialAccountId: ID!, $closingDate: Date) {
    creditCardBill(financialAccountId: $financialAccountId, closingDate: $closingDate) {
      id
      financialAccountId
      closingDate
      dueDate
      totalAmount
      minimumPayment
      status
      createdAt
      updatedAt
    }
  }
`;

/** Holdings for one investment account. */
export const INVESTMENT_HOLDINGS = gql`
  query InvestmentHoldings($financialAccountId: ID!) {
    investmentHoldings(financialAccountId: $financialAccountId) {
      id
      quantity
      averageCost
      grossValue
      netValue
      incomeTax
      iof
      valuationSource
      valuationReferenceDate
      isExpired
      indexer
      indexerPercentage
      preFixedRate
      issueDate
      maturityDate
      isTaxExempt
      source
      asset {
        id
        name
        assetType
        ticker
        issuerName
        currencyCode
      }
    }
  }
`;

/** Holdings across all investment accounts (consolidated mode). */
export const ALL_INVESTMENT_HOLDINGS = gql`
  query AllInvestmentHoldings {
    allInvestmentHoldings {
      id
      quantity
      averageCost
      grossValue
      netValue
      incomeTax
      iof
      valuationSource
      valuationReferenceDate
      isExpired
      indexer
      indexerPercentage
      preFixedRate
      issueDate
      maturityDate
      isTaxExempt
      source
      financialAccount {
        id
        type
        subtype
        name
        currencyCode
        themeColor
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
        ticker
        issuerName
        currencyCode
      }
    }
  }
`;

/** Fetch a single PDF import job by ID — used for polling during extraction. */
export const BILL_IMPORT_JOB = gql`
  query BillImportJob($jobId: ID!) {
    billImportJob(jobId: $jobId) {
      id
      status
      fileName
      createdAt
      errorMessage
      errorCode
      errorParameters
      reviewPayload {
        bankName
        reviewMode
        cards {
          reviewCardId
          lastFourDigits
          holderName
          computedTotal
          declaredTotal
          totalMatchesDeclared
          matchedFinancialAccountId
          matchAmbiguous
          closingDate
          dueDate
          matchCandidates {
            id
            name
          }
          transactions {
            date
            description
            amount
            creditDebitType
            isBillPayment
            installment {
              current
              total
            }
          }
        }
      }
    }
  }
`;

/** Transactions belonging to a specific credit card bill (Bill mode). */
export const TRANSACTIONS_BY_BILL = gql`
  query GetTransactionsByBill($billId: ID!, $first: Int, $after: String) {
    transactions(billId: $billId, first: $first, after: $after) {
      edges {
        node {
          id
          description
          category {
            id
            name
            color
            icon
          }
          type
          amount
          creditDebitType
          tags
          currencyCode
          date
          status
          financialAccountId
          billId
          installmentNumber
          totalInstallments
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;
