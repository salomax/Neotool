import { gql } from '@apollo/client';

/** Create a new financial account or card. */
export const CREATE_FINANCIAL_ACCOUNT = gql`
  mutation CreateFinancialAccount($input: CreateFinancialAccountInput!) {
    createFinancialAccount(input: $input) {
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

export const UPDATE_FINANCIAL_ACCOUNT = gql`
  mutation UpdateFinancialAccount($id: ID!, $input: UpdateFinancialAccountInput!) {
    updateFinancialAccount(id: $id, input: $input) {
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

export const UPDATE_ACCOUNT_PREFERENCES = gql`
  mutation UpdateAccountPreferences($input: UpdateAccountPreferencesInput!) {
    updateAccountPreferences(input: $input) {
      defaultTransactionStatus
    }
  }
`;

export const UPDATE_BANKING_USER_PREFERENCES = gql`
  mutation UpdateBankingUserPreferences($input: UpdateBankingUserPreferencesInput!) {
    updateBankingUserPreferences(input: $input) {
      ruleApplicationMode
    }
  }
`;

/** Create an investment asset in the global catalog. */
export const CREATE_INVESTMENT_ASSET = gql`
  mutation CreateInvestmentAsset($input: CreateInvestmentAssetInput!) {
    createInvestmentAsset(input: $input) {
      id
      name
      assetType
      issuerName
      currencyCode
    }
  }
`;

/** Record an investment transaction (creates/updates holding). */
export const RECORD_INVESTMENT_TRANSACTION = gql`
  mutation RecordInvestmentTransaction($input: RecordInvestmentTransactionInput!) {
    recordInvestmentTransaction(input: $input) {
      id
      type
      direction
      quantity
      unitPrice
      grossValue
      tradeDate
      source
    }
  }
`;

/** Manual cash on an investment/benefit custodian account (aporte, saque, ajuste). */
export const CREATE_INVESTMENT_CASH_TRANSACTION = gql`
  mutation CreateInvestmentCashTransaction($input: CreateInvestmentCashTransactionInput!) {
    createInvestmentCashTransaction(input: $input) {
      id
      financialAccountId
      creditDebitType
      type
      amount
      date
      description
      transferPeerTransactionId
    }
  }
`;

export { DELETE_INVESTMENT_CASH_TRANSACTION, UPDATE_INVESTMENT_CASH_TRANSACTION } from "./investmentCashMutations";

export const DELETE_INVESTMENT_TRANSACTION = gql`
  mutation DeleteInvestmentTransaction($id: ID!) {
    deleteInvestmentTransaction(id: $id)
  }
`;

export const DELETE_INVESTMENT_HOLDING = gql`
  mutation DeleteInvestmentHolding($id: ID!) {
    deleteInvestmentHolding(id: $id)
  }
`;

/** Delete a transaction. */
export const DELETE_TRANSACTION = gql`
  mutation DeleteTransaction($id: ID!) {
    deleteTransaction(id: $id)
  }
`;

/** Create a new manual transaction. */
export const CREATE_TRANSACTION = gql`
  mutation CreateTransaction($input: CreateTransactionInput!) {
    createTransaction(input: $input) {
      transaction {
        id
        financialAccountId
        belongsToUserId
        belongsToName
        creditDebitType
        type
        status
        amount
        currencyCode
        date
        description
        notes
        tags
        category {
          id
          name
        }
      }
      appliedRules {
        id
      }
    }
  }
`;

/** Start a file-based transaction import (auto-discovery for OFX/CSV/XLSX/PDF). */
export const IMPORT_TRANSACTIONS_FILE = gql`
  mutation ImportTransactionsFile($input: ImportTransactionsFileInput!) {
    importTransactionsFile(input: $input) {
      import {
        id
        status
        totalRows
        importedCount
        skippedCount
        duplicateReviewCount
        errorCount
      }
    }
  }
`;

/** Import a single transaction from an NFC-e QR code URL (synchronous). */
export const IMPORT_NFCE = gql`
  mutation ImportNfce($financialAccountId: ID!, $qrCodeUrl: String!) {
    importNfce(financialAccountId: $financialAccountId, qrCodeUrl: $qrCodeUrl) {
      import {
        id
        status
        importedCount
        skippedCount
      }
    }
  }
`;

/** Resolve one duplicate candidate on an import job in REVIEW_REQUIRED. */
export const RESOLVE_IMPORT_DUPLICATE = gql`
  mutation ResolveImportDuplicate($input: ResolveImportDuplicateInput!) {
    resolveImportDuplicate(input: $input) {
      id
      status
      duplicateReviewCount
      duplicateCandidates {
        rowIndex
        parsed {
          creditDebitType
          amount
          date
          description
          currencyCode
        }
        candidates {
          id
          date
          description
          amount
          source
        }
      }
    }
  }
`;

export const CREATE_PRODUCT = gql`
  mutation CreateProduct($input: CreateProductInput!) {
    createProduct(input: $input) {
      id
      name
      ncmCode
      defaultUnit
      category
    }
  }
`;

export const UPDATE_PRODUCT = gql`
  mutation UpdateProduct($id: ID!, $input: UpdateProductInput!) {
    updateProduct(id: $id, input: $input) {
      id
      name
      ncmCode
      defaultUnit
      category
    }
  }
`;

export const CREATE_PRODUCT_VARIANT = gql`
  mutation CreateProductVariant($input: CreateProductVariantInput!) {
    createProductVariant(input: $input) {
      id
      productId
      name
      brand
      ean
      quantityValue
      quantityUnit
      normalizedQuantityValue
      normalizedQuantityUnit
    }
  }
`;

export const UPDATE_PRODUCT_VARIANT = gql`
  mutation UpdateProductVariant($id: ID!, $input: UpdateProductVariantInput!) {
    updateProductVariant(id: $id, input: $input) {
      id
      productId
      name
      brand
      ean
      quantityValue
      quantityUnit
      normalizedQuantityValue
      normalizedQuantityUnit
    }
  }
`;

export const MAP_ITEM_TO_VARIANT = gql`
  mutation MapItemToVariant($rawDescription: String!, $variantId: ID!) {
    mapItemToVariant(rawDescription: $rawDescription, variantId: $variantId) {
      id
      productVariantId
      rawDescription
      source
      confirmed
    }
  }
`;

export const UNMAP_ITEM = gql`
  mutation UnmapItem($aliasId: ID!) {
    unmapItem(aliasId: $aliasId)
  }
`;

/** Update an existing transaction. */
export const UPDATE_TRANSACTION = gql`
  mutation UpdateTransaction($id: ID!, $input: UpdateTransactionInput!) {
    updateTransaction(id: $id, input: $input) {
      id
      financialAccountId
      belongsToUserId
      belongsToName
      creditDebitType
      type
      status
      amount
      currencyCode
      date
      description
      notes
      tags
      appliedRuleIds
      category {
        id
        name
      }
    }
  }
`;

export const APPLY_TRANSACTION_EDIT_TO_SIMILAR = gql`
  mutation ApplyTransactionEditToSimilar(
    $id: ID!
    $matchDescription: String!
    $input: UpdateTransactionInput!
    $applyToFuture: Boolean
    $applyToExisting: Boolean
  ) {
    applyTransactionEditToSimilar(
      id: $id
      matchDescription: $matchDescription
      input: $input
      applyToFuture: $applyToFuture
      applyToExisting: $applyToExisting
    ) {
      affectedCount
      futureRuleCreated
    }
  }
`;

/** Submit a PDF or CSV file for credit card bill extraction (async). Returns a BillImportJob in EXTRACTING status. */
export const SUBMIT_BILL_IMPORT = gql`
  mutation SubmitBillImport($input: SubmitBillImportInput!) {
    submitBillImport(input: $input) {
      id
      status
      fileName
      createdAt
      errorMessage
      errorCode
      errorParameters
    }
  }
`;

/** Confirm a reviewed PDF bill import — saves transactions and resolves card associations. */
export const CONFIRM_BILL_IMPORT = gql`
  mutation ConfirmBillImport($input: ConfirmBillImportInput!) {
    confirmBillImport(input: $input) {
      id
      status
      createdAt
      errorMessage
      errorCode
      errorParameters
    }
  }
`;

/** Register a payment against a credit card bill (creates TRANSFER transactions and updates bill status). */
export const PAY_BILL = gql`
  mutation PayBill($billId: ID!, $amount: Float!, $sourceAccountId: ID) {
    payBill(billId: $billId, amount: $amount, sourceAccountId: $sourceAccountId) {
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

/** Create an account-scoped transaction category. */
export const CREATE_TRANSACTION_CATEGORY = gql`
  mutation CreateTransactionCategory($input: CreateCategoryInput!) {
    createTransactionCategory(input: $input) {
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

/** Update an account-scoped transaction category (name, icon, color, sortOrder). */
export const UPDATE_TRANSACTION_CATEGORY = gql`
  mutation UpdateTransactionCategory($id: ID!, $input: UpdateCategoryInput!) {
    updateTransactionCategory(id: $id, input: $input) {
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

/** Soft-delete an account-scoped category (and subcategories if root). */
export const DELETE_TRANSACTION_CATEGORY = gql`
  mutation DeleteTransactionCategory($id: ID!) {
    deleteTransactionCategory(id: $id)
  }
`;

// ── Asset service upload flow ─────────────────────────────────────────────────

/**
 * Step 1 of 3: create an upload slot and obtain a presigned PUT URL.
 * Use namespace "banking-attachments" for transaction/holding files.
 */
export const CREATE_ASSET_UPLOAD = gql`
  mutation CreateAssetUpload($input: CreateAssetUploadInput!) {
    createAssetUpload(input: $input) {
      id
      uploadUrl
      status
      visibility
    }
  }
`;

/** Step 3 of 3: mark the asset as READY after the client PUT the file to uploadUrl. */
export const CONFIRM_ASSET_UPLOAD = gql`
  mutation ConfirmAssetUpload($input: ConfirmAssetUploadInput!) {
    confirmAssetUpload(input: $input) {
      id
      status
      visibility
      publicUrl
    }
  }
`;

// ── Banking attachments ───────────────────────────────────────────────────────

/** Attach a confirmed asset to a transaction. Max 3 per transaction. */
export const ATTACH_FILE_TO_TRANSACTION = gql`
  mutation AttachFileToTransaction($input: AttachFileToTransactionInput!) {
    attachFileToTransaction(input: $input) {
      id
      assetId
      filename
      mimeType
      sizeBytes
      downloadUrl
      createdAt
    }
  }
`;

/** Remove an attachment from a transaction (also deletes the asset). */
export const REMOVE_TRANSACTION_ATTACHMENT = gql`
  mutation RemoveTransactionAttachment($attachmentId: ID!) {
    removeTransactionAttachment(attachmentId: $attachmentId)
  }
`;

/** Attach a confirmed asset to an investment holding. Max 3 per holding. */
export const ATTACH_FILE_TO_HOLDING = gql`
  mutation AttachFileToHolding($input: AttachFileToHoldingInput!) {
    attachFileToHolding(input: $input) {
      id
      assetId
      filename
      mimeType
      sizeBytes
      downloadUrl
      createdAt
    }
  }
`;

/** Remove an attachment from a holding (also deletes the asset). */
export const REMOVE_HOLDING_ATTACHMENT = gql`
  mutation RemoveHoldingAttachment($attachmentId: ID!) {
    removeHoldingAttachment(attachmentId: $attachmentId)
  }
`;

// ── Transaction rules ─────────────────────────────────────────────────────────

const RULE_RESPONSE_FIELDS = `
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
  category { id name icon color applicableTo }
  merchantId
  merchant { id name domain logoAsset { id publicUrl } }
`;

export const CREATE_TRANSACTION_RULE = gql`
  mutation CreateTransactionRule($input: CreateTransactionRuleInput!) {
    createTransactionRule(input: $input) {
      ${RULE_RESPONSE_FIELDS}
    }
  }
`;

export const UPDATE_TRANSACTION_RULE = gql`
  mutation UpdateTransactionRule($id: ID!, $input: UpdateTransactionRuleInput!) {
    updateTransactionRule(id: $id, input: $input) {
      ${RULE_RESPONSE_FIELDS}
    }
  }
`;

export const DELETE_TRANSACTION_RULE = gql`
  mutation DeleteTransactionRule($id: ID!) {
    deleteTransactionRule(id: $id)
  }
`;

export const REORDER_TRANSACTION_RULES = gql`
  mutation ReorderTransactionRules($ids: [ID!]!) {
    reorderTransactionRules(ids: $ids) {
      ${RULE_RESPONSE_FIELDS}
    }
  }
`;

export const SET_SYSTEM_TRANSACTION_RULE_ENABLED = gql`
  mutation SetSystemTransactionRuleEnabled($id: ID!, $enabled: Boolean!) {
    setSystemTransactionRuleEnabled(id: $id, enabled: $enabled)
  }
`;
