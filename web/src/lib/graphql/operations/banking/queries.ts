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
    $tags: [String!]
    $categoryId: ID
    $creditDebitType: CreditDebitType
    $first: Int
    $after: String
  ) {
    transactions(
      financialAccountId: $financialAccountId
      billId: $billId
      month: $month
      year: $year
      tags: $tags
      categoryId: $categoryId
      creditDebitType: $creditDebitType
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
          merchant {
            id
            name
            logoAsset {
              id
              publicUrl
            }
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
          attachments {
            id
            assetId
            filename
            mimeType
            sizeBytes
            downloadUrl
            createdAt
          }
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;

/**
 * Category breakdown with the same filter surface as TRANSACTIONS.
 * Use this alongside TRANSACTIONS so both reflect the same set of transactions.
 */
export const TRANSACTIONS_BY_CATEGORY = gql`
  query TransactionsByCategory(
    $financialAccountId: ID
    $belongsToUserId: ID
    $belongsToName: String
    $tags: [String!]
    $billId: ID
    $categoryId: ID
    $uncategorized: Boolean
    $month: Int
    $year: Int
    $applicableTo: CategoryApplicableTo
  ) {
    transactionsByCategory(
      financialAccountId: $financialAccountId
      belongsToUserId: $belongsToUserId
      belongsToName: $belongsToName
      tags: $tags
      billId: $billId
      categoryId: $categoryId
      uncategorized: $uncategorized
      month: $month
      year: $year
      applicableTo: $applicableTo
    ) {
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
`;

/**
 * Tag breakdown with the same filter surface as TRANSACTIONS.
 * Use this alongside TRANSACTIONS so both reflect the same set of transactions.
 */
export const TRANSACTIONS_BY_TAG = gql`
  query TransactionsByTag(
    $financialAccountId: ID
    $belongsToUserId: ID
    $belongsToName: String
    $tags: [String!]
    $billId: ID
    $categoryId: ID
    $uncategorized: Boolean
    $month: Int
    $year: Int
    $applicableTo: CategoryApplicableTo
  ) {
    transactionsByTag(
      financialAccountId: $financialAccountId
      belongsToUserId: $belongsToUserId
      belongsToName: $belongsToName
      tags: $tags
      billId: $billId
      categoryId: $categoryId
      uncategorized: $uncategorized
      month: $month
      year: $year
      applicableTo: $applicableTo
    ) {
      tag
      totalAmount
      transactionCount
      percentage
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
      tagsSummary {
        tag
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
      pricingMode
      grossValue
      netValue
      incomeTax
      iof
      valuationSource
      valuationReferenceDate
      isExpired
      isClosed
      isExpiredPending
      closedAt
      closeReason
      indexer
      indexerPercentage
      fixedAnnualRate
      issueDate
      maturityDate
      isTaxExempt
      source
      attachments {
        id
        assetId
        filename
        mimeType
        sizeBytes
        downloadUrl
        createdAt
      }
      asset {
        id
        name
        assetType
        ticker
        issuerName
        currencyCode
        logoAsset {
          id
          publicUrl
        }
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
      pricingMode
      grossValue
      netValue
      incomeTax
      iof
      valuationSource
      valuationReferenceDate
      isExpired
      isClosed
      isExpiredPending
      closedAt
      closeReason
      indexer
      indexerPercentage
      fixedAnnualRate
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
        logoAsset {
          id
          publicUrl
        }
      }
    }
  }
`;

export const INVESTMENT_TRANSACTIONS = gql`
  query InvestmentTransactions($holdingId: ID!) {
    investmentTransactions(holdingId: $holdingId) {
      id
      holdingId
      financialAccountId
      type
      direction
      quantity
      unitPrice
      grossValue
      tradeDate
      settlementDate
      source
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
        statementPeriod {
          from
          to
        }
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
            categoryId
            categorySource
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
          merchant {
            id
            name
            logoAsset {
              id
              publicUrl
            }
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
