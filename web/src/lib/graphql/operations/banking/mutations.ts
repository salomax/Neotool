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
  }
`;

/** Start a file-based transaction import (auto-discovery for OFX/CSV and future formats). */
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
      category {
        id
        name
      }
    }
  }
`;
