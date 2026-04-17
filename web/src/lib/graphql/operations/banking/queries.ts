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
      includeCashInAllocation
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
        linkedBankAccountId
      }
      connectionStatus
      consentExpiresAt
      lastSyncAt
      createdAt
      updatedAt
    }
  }
`;

export const ACCOUNT_PREFERENCES = gql`
  query AccountPreferences {
    accountPreferences {
      defaultTransactionStatus
    }
  }
`;

export const BANKING_USER_PREFERENCES = gql`
  query BankingUserPreferences {
    bankingUserPreferences {
      ruleApplicationMode
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
    $search: String
    $tags: [String!]
    $categoryId: ID
    $uncategorized: Boolean
    $creditDebitType: CreditDebitType
    $types: [TransactionType!]
    $first: Int
    $after: String
  ) {
    transactions(
      financialAccountId: $financialAccountId
      billId: $billId
      month: $month
      year: $year
      search: $search
      tags: $tags
      categoryId: $categoryId
      uncategorized: $uncategorized
      creditDebitType: $creditDebitType
      types: $types
      first: $first
      after: $after
    ) {
      edges {
        node {
          id
          description
          source
          hasNfceDocument
          appliedRuleIds
          appliedRuleHistory {
            ruleId
            appliedAt
          }
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
      uncategorized
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

export const SIMILAR_TRANSACTION_EDIT_PREVIEW = gql`
  query SimilarTransactionEditPreview($id: ID!) {
    similarTransactionEditPreview(id: $id) {
      similarCount
      matchDescription
      supportsFuturePropagation
    }
  }
`;

export const PRODUCTS = gql`
  query Products($search: String, $category: String) {
    products(search: $search, category: $category) {
      id
      name
      ncmCode
      defaultUnit
      category
      averagePrice
      totalSpent
      purchaseCount
      variants {
        id
        productId
        name
        brand
        ean
        quantityValue
        quantityUnit
        normalizedQuantityValue
        normalizedQuantityUnit
        averageUnitPrice
      }
    }
  }
`;

export const PRODUCT_VARIANTS = gql`
  query ProductVariants($search: String, $productId: ID, $limit: Int) {
    productVariants(search: $search, productId: $productId, limit: $limit) {
      id
      productId
      name
      brand
      ean
      quantityValue
      quantityUnit
      normalizedQuantityValue
      normalizedQuantityUnit
      averageUnitPrice
    }
  }
`;

export const PRODUCT_PRICE_HISTORY = gql`
  query ProductPriceHistory($productId: ID!, $variantId: ID, $startDate: Date, $endDate: Date) {
    productPriceHistory(productId: $productId, variantId: $variantId, startDate: $startDate, endDate: $endDate) {
      date
      unitPrice
      storeName
      municipality
      state
    }
  }
`;

export const PRODUCT_CONSUMPTION_TREND = gql`
  query ProductConsumptionTrend($productId: ID!, $periodMonths: Int) {
    productConsumptionTrend(productId: $productId, periodMonths: $periodMonths) {
      productId
      periodMonths
      totalQuantity
      totalSpent
      purchaseCount
      months {
        month
        totalQuantity
        totalSpent
        purchaseCount
      }
    }
  }
`;

export const PRODUCT_STORE_COMPARISON = gql`
  query ProductStoreComparison($productId: ID!, $startDate: Date, $endDate: Date) {
    productStoreComparison(productId: $productId, startDate: $startDate, endDate: $endDate) {
      storeName
      municipality
      state
      averageUnitPrice
      purchaseCount
      lastPurchaseDate
    }
  }
`;

export const SPENDING_BY_NCM = gql`
  query SpendingByNcm($depth: Int, $startDate: Date, $endDate: Date) {
    spendingByNcm(depth: $depth, startDate: $startDate, endDate: $endDate) {
      ncmCode
      ncmDescription
      totalSpent
      itemCount
      months {
        month
        totalSpent
        itemCount
      }
    }
  }
`;

export const UNMAPPED_ITEMS = gql`
  query UnmappedItems($limit: Int) {
    unmappedItems(limit: $limit) {
      rawDescription
      count
      sampleItems {
        id
        transactionId
        rawDescription
        quantity
        unit
        unitPrice
        totalPrice
        discount
        effectivePrice
        ncmCode
        ean
        issuerProductCode
        createdAt
      }
    }
  }
`;

export const NFCE_DOCUMENT = gql`
  query NfceDocument($transactionId: ID!) {
    nfceDocument(transactionId: $transactionId) {
      id
      transactionId
      accessKey
      issuerCnpj
      issuerName
      issuerTradeName
      issuerStateRegistration
      issuerAddress
      issuerPostalCode
      emissionDate
      totalValue
      totalTax
      buyerCpf
      municipalityCode
      municipality
      state
      countryCode
      sourceUrl
      createdAt
      items {
        id
        sequence
        rawDescription
        ncmCode
        ean
        issuerProductCode
        quantity
        unit
        unitPrice
        totalPrice
        discount
        effectivePrice
        normalizedUnitPrice
        taxAmount
        productVariant {
          id
          name
          brand
          quantityValue
          quantityUnit
          normalizedQuantityValue
          normalizedQuantityUnit
        }
      }
    }
  }
`;

/** Single transaction import job by ID (includes duplicate candidates for review). */
export const TRANSACTION_IMPORT = gql`
  query TransactionImport($id: ID!) {
    transactionImport(id: $id) {
      id
      source
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
        uncategorized
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
        uncategorized
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

/** Holdings for one investment account, or consolidated across all accounts when financialAccountId is omitted. */
export const INVESTMENT_HOLDINGS = gql`
  query InvestmentHoldings($financialAccountId: ID, $assetClass: InvestmentHoldingAssetClass) {
    investmentHoldings(financialAccountId: $financialAccountId, assetClass: $assetClass) {
      id
      financialAccountId
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
      couponFrequency
      couponDayOfMonth
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
      createdAt
      updatedAt
    }
  }
`;

/** Minimal dataset for investments grid cards (single request). */
export const INVESTMENT_GRID_DATA = gql`
  query InvestmentGridData($financialAccountId: ID, $includeProjection: Boolean) {
    investmentCashBalance(financialAccountId: $financialAccountId)
    investmentHoldings(financialAccountId: $financialAccountId) {
      id
      quantity
      averageCost
      grossValue
      asset {
        assetType
      }
    }
    perfCash: portfolioPerformance(
      financialAccountId: $financialAccountId
      groupKey: "cash"
      resolution: MONTH
      includeProjection: $includeProjection
    ) {
      groupKey
      label
      includedHoldingsCount
      points {
        timestamp
        grossValue
      }
    }
    twrCash: gridClassTwrSeries(financialAccountId: $financialAccountId, gridClassKey: "cash", period: ONE_YEAR) {
      date
      quotaValue
    }
    twrFixedIncome: gridClassTwrSeries(
      financialAccountId: $financialAccountId
      gridClassKey: "fixed-income"
      period: ONE_YEAR
    ) {
      date
      quotaValue
    }
    twrFunds: gridClassTwrSeries(financialAccountId: $financialAccountId, gridClassKey: "funds", period: ONE_YEAR) {
      date
      quotaValue
    }
    twrStocks: gridClassTwrSeries(financialAccountId: $financialAccountId, gridClassKey: "stocks", period: ONE_YEAR) {
      date
      quotaValue
    }
    twrReits: gridClassTwrSeries(financialAccountId: $financialAccountId, gridClassKey: "reits", period: ONE_YEAR) {
      date
      quotaValue
    }
    twrCrypto: gridClassTwrSeries(financialAccountId: $financialAccountId, gridClassKey: "crypto", period: ONE_YEAR) {
      date
      quotaValue
    }
    perfFixedIncome: portfolioPerformance(
      financialAccountId: $financialAccountId
      groupKey: "fixedIncome"
      resolution: MONTH
      includeProjection: $includeProjection
    ) {
      groupKey
      label
      includedHoldingsCount
      points {
        timestamp
        grossValue
      }
    }
    perfMutualFunds: portfolioPerformance(
      financialAccountId: $financialAccountId
      groupKey: "mutualFunds"
      resolution: MONTH
      includeProjection: $includeProjection
    ) {
      groupKey
      label
      includedHoldingsCount
      points {
        timestamp
        grossValue
      }
    }
    perfPension: portfolioPerformance(
      financialAccountId: $financialAccountId
      groupKey: "pension"
      resolution: MONTH
      includeProjection: $includeProjection
    ) {
      groupKey
      label
      includedHoldingsCount
      points {
        timestamp
        grossValue
      }
    }
    perfStocks: portfolioPerformance(
      financialAccountId: $financialAccountId
      subgroupKey: "perfStocks"
      resolution: MONTH
      includeProjection: $includeProjection
    ) {
      groupKey
      label
      includedHoldingsCount
      points {
        timestamp
        grossValue
      }
    }
    perfReits: portfolioPerformance(
      financialAccountId: $financialAccountId
      subgroupKey: "perfReits"
      resolution: MONTH
      includeProjection: $includeProjection
    ) {
      groupKey
      label
      includedHoldingsCount
      points {
        timestamp
        grossValue
      }
    }
    perfCrypto: portfolioPerformance(
      financialAccountId: $financialAccountId
      subgroupKey: "perfCrypto"
      resolution: MONTH
      includeProjection: $includeProjection
    ) {
      groupKey
      label
      includedHoldingsCount
      points {
        timestamp
        grossValue
      }
    }
  }
`;

/** Ledger cash balance (CREDIT − DEBIT, posted/pending) on investment custodian account(s). */
export const INVESTMENT_CASH_BALANCE = gql`
  query InvestmentCashBalance($financialAccountId: ID) {
    investmentCashBalance(financialAccountId: $financialAccountId)
  }
`;

/** Paginated cash-related ledger rows on investment/benefit custodian account(s). */
export const INVESTMENT_CASH_TRANSACTIONS = gql`
  query InvestmentCashTransactions($financialAccountId: ID, $first: Int, $after: String) {
    investmentCashTransactions(financialAccountId: $financialAccountId, first: $first, after: $after) {
      edges {
        cursor
        node {
          id
          date
          description
          type
          creditDebitType
          amount
          currencyCode
          financialAccountId
          source
          status
          transferPeerTransactionId
          investmentTransactionId
          linkedInvestmentAsset {
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
          category {
            id
            name
          }
        }
      }
      pageInfo {
        hasNextPage
        endCursor
        startCursor
        hasPreviousPage
      }
    }
  }
`;

export const INVESTMENT_PORTFOLIO_SUMMARY = gql`
  query InvestmentPortfolioSummary($financialAccountId: ID, $period: PerformancePeriod) {
    investmentPortfolioSummary(financialAccountId: $financialAccountId, period: $period) {
      totalHoldingsCount
      totalCostBasis
      totalGrossValue
      totalNetValue
      totalIncomeTax
      totalIof
      grossGain
      grossGainPercent
      netGain
      netGainPercent
      cashBalance
      totalPortfolioValue
      hasEstimatedValues
      valuationReferenceDate
      currencyCode
      byAssetGroup {
        groupKey
        label
        count
        totalCostBasis
        totalGrossValue
        grossGainPercent
        twr
        allocationPercent
        isCash
      }
      performance {
        twr
        twrSince
        twrAbsolute
        period
        hasEstimatedValues
        benchmarks {
          code
          label
          twr
        }
      }
      recalculationStatus {
        isCalculating
        jobId
        startedAt
        errorCode
        message
      }
    }
  }
`;

export const PORTFOLIO_PERFORMANCE = gql`
  query PortfolioPerformance(
    $financialAccountId: ID
    $groupKey: String
    $resolution: HoldingPerformanceResolution
    $includeProjection: Boolean
  ) {
    portfolioPerformance(
      financialAccountId: $financialAccountId
      groupKey: $groupKey
      resolution: $resolution
      includeProjection: $includeProjection
    ) {
      groupKey
      label
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
      excludedHoldingsCount
      includedHoldingsCount
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
/** Paginated merchants (system + user-created) available to the authenticated account. */
export const MERCHANTS = gql`
  query Merchants($search: String, $after: String, $first: Int = 20) {
    merchants(search: $search, after: $after, first: $first) {
      edges {
        node {
          id
          name
          domain
          isSystem
          logoAsset {
            id
            publicUrl
          }
        }
        cursor
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
      totalCount
    }
  }
`;

// ── Transaction rules ─────────────────────────────────────────────────────────

/** Fragment reused by all rule queries and mutations. */
export const TRANSACTION_RULE_FIELDS = gql`
  fragment TransactionRuleFields on TransactionRule {
    id
    pattern
    patternType
    applicableTo
    priority
    isSystem
    isDisabledForAccount
    isImplicit
    matchCount
    createdAt
    descriptionOverride
    tagsOverride
    categoryId
    category {
      id
      name
      icon
      color
      applicableTo
    }
    merchantId
    merchant {
      id
      name
      domain
      logoAsset {
        id
        publicUrl
      }
    }
  }
`;

/** Transaction rules filtered by ownership (manual/user vs automatic/system). */
export const TRANSACTION_RULES = gql`
  ${TRANSACTION_RULE_FIELDS}
  query TransactionRules($isSystem: Boolean, $search: String, $first: Int, $after: String) {
    transactionRules(isSystem: $isSystem, search: $search, first: $first, after: $after) {
      edges {
        node {
          ...TransactionRuleFields
        }
        cursor
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
      totalCount
    }
  }
`;

/** Specific transaction rules fetched by ID, preserving input order. */
export const TRANSACTION_RULES_BY_IDS = gql`
  ${TRANSACTION_RULE_FIELDS}
  query TransactionRulesByIds($ids: [ID!]!) {
    transactionRulesByIds(ids: $ids) {
      ...TransactionRuleFields
      position
    }
  }
`;

export const TRANSACTION_RULE_HISTORICAL_MATCH_COUNT = gql`
  query TransactionRuleHistoricalMatchCount($id: ID!) {
    transactionRuleHistoricalMatchCount(id: $id)
  }
`;

/** Tests whether a pattern matches a sample string. */
export const PREVIEW_TRANSACTION_RULE = gql`
  query PreviewTransactionRule($pattern: String!, $patternType: PatternType!, $sample: String!) {
    previewTransactionRule(pattern: $pattern, patternType: $patternType, sample: $sample)
  }
`;

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
