package io.github.salomax.neotool.banking.error

import io.github.salomax.neotool.common.error.ErrorCode

/**
 * Banking domain error codes.
 * These codes are sent to the frontend for i18n translation.
 */
enum class BankingErrorCode(
    override val code: String,
    override val defaultMessage: String,
    override val httpStatus: Int = 400,
) : ErrorCode {
    // Category errors
    CATEGORY_NOT_FOUND(
        "CATEGORY_NOT_FOUND",
        "Category not found",
        404,
    ),
    CATEGORY_DEPTH_EXCEEDED(
        "CATEGORY_DEPTH_EXCEEDED",
        "Category hierarchy depth exceeded",
        422,
    ),
    CATEGORY_SYSTEM_NOT_EDITABLE(
        "CATEGORY_SYSTEM_NOT_EDITABLE",
        "System categories cannot be modified",
        409,
    ),
    CATEGORY_APPLICABLE_TO_MISMATCH(
        "CATEGORY_APPLICABLE_TO_MISMATCH",
        "Category applicable type is not compatible with parent category",
        422,
    ),
    CATEGORY_NAME_ALREADY_EXISTS(
        "CATEGORY_NAME_ALREADY_EXISTS",
        "Category name already exists",
        409,
    ),
    CATEGORY_RULE_NOT_FOUND(
        "CATEGORY_RULE_NOT_FOUND",
        "Category rule not found",
        404,
    ),
    CATEGORY_RULE_SYSTEM_NOT_DELETABLE(
        "CATEGORY_RULE_SYSTEM_NOT_DELETABLE",
        "System category rules cannot be deleted",
        409,
    ),
    CATEGORY_RULE_SYSTEM_NOT_EDITABLE(
        "CATEGORY_RULE_SYSTEM_NOT_EDITABLE",
        "System category rules cannot be modified",
        409,
    ),
    CATEGORY_RULE_DUPLICATE(
        "CATEGORY_RULE_DUPLICATE",
        "A category rule with the same pattern and type already exists",
        409,
    ),
    CATEGORY_RULE_INVALID_REGEX(
        "CATEGORY_RULE_INVALID_REGEX",
        "Invalid REGEX pattern",
        422,
    ),

    // Merchant errors
    MERCHANT_NOT_FOUND(
        "MERCHANT_NOT_FOUND",
        "Merchant not found",
        404,
    ),

    // Import lifecycle errors
    IMPORT_FILE_ALREADY_EXISTS(
        "IMPORT_FILE_ALREADY_EXISTS",
        "This file was already imported or is currently being processed for this account",
        409,
    ),
    DUPLICATE_IMPORT(
        "DUPLICATE_IMPORT",
        "This file has already been imported for this account",
        409,
    ),
    IMPORT_ASYNC_PIPELINE_UNAVAILABLE(
        "IMPORT_ASYNC_PIPELINE_UNAVAILABLE",
        "Import processing pipeline is unavailable (kafka.enabled=false)",
        503,
    ),
    IMPORT_JOB_START_FAILED(
        "IMPORT_JOB_START_FAILED",
        "Failed to queue import job for asynchronous processing",
        503,
    ),
    IMPORT_ASSET_SERVICE_UNAVAILABLE(
        "IMPORT_ASSET_SERVICE_UNAVAILABLE",
        "Assets integration is temporarily unavailable",
        503,
    ),
    IMPORT_ASSET_INVALID_RESPONSE(
        "IMPORT_ASSET_INVALID_RESPONSE",
        "Assets integration returned an invalid response",
        503,
    ),
    IMPORT_ASSET_NOT_FOUND(
        "IMPORT_ASSET_NOT_FOUND",
        "Import asset was not found or is not accessible",
        404,
    ),
    IMPORT_ASSET_DOWNLOAD_URL_MISSING(
        "IMPORT_ASSET_DOWNLOAD_URL_MISSING",
        "Import asset is missing download URL",
        409,
    ),
    IMPORT_ASSET_TRANSFER_FAILED(
        "IMPORT_ASSET_TRANSFER_FAILED",
        "Failed to transfer file to or from assets storage",
        503,
    ),
    IMPORT_FILE_FORMAT_NOT_SUPPORTED(
        "IMPORT_FILE_FORMAT_NOT_SUPPORTED",
        "Import file format is not supported",
        422,
    ),
    PDF_PASSWORD_REQUIRED(
        "PDF_PASSWORD_REQUIRED",
        "The PDF is password-protected and requires a valid password",
        422,
    ),
    IMPORT_OFX_DESTINATION_MISMATCH(
        "IMPORT_OFX_DESTINATION_MISMATCH",
        "OFX statement type is not compatible with the destination financial account",
        422,
    ),
    TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW(
        "TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW",
        "Transaction date must be within the last 10 years",
        422,
    ),
    TRANSACTION_BEFORE_INITIAL_BALANCE(
        "TRANSACTION_BEFORE_INITIAL_BALANCE",
        "Transaction date is before the account's initial balance date",
        422,
    ),
    TRANSACTION_NOT_FOUND(
        "TRANSACTION_NOT_FOUND",
        "Transaction not found",
        404,
    ),
    FINANCIAL_ACCOUNT_HAS_TRANSACTIONS(
        "FINANCIAL_ACCOUNT_HAS_TRANSACTIONS",
        "Financial account has transactions and cannot be deleted",
        409,
    ),
    FINANCIAL_ACCOUNT_HAS_OPEN_BILLS(
        "FINANCIAL_ACCOUNT_HAS_OPEN_BILLS",
        "Financial account has open bills and cannot be deleted",
        409,
    ),
    CARD_MASKED_NUMBER_INVALID(
        "CARD_MASKED_NUMBER_INVALID",
        "Card last four digits must be empty or exactly 4 digits",
        422,
    ),
    FINANCIAL_ACCOUNT_NAME_TOO_LONG(
        "FINANCIAL_ACCOUNT_NAME_TOO_LONG",
        "Financial account name exceeds the maximum length",
        422,
    ),

    // Attachment errors
    ATTACHMENT_LIMIT_REACHED(
        "ATTACHMENT_LIMIT_REACHED",
        "Maximum of 3 attachments per record reached",
        422,
    ),
    ATTACHMENT_NOT_FOUND(
        "ATTACHMENT_NOT_FOUND",
        "Attachment not found",
        404,
    ),
    ATTACHMENT_ASSET_NOT_READY(
        "ATTACHMENT_ASSET_NOT_READY",
        "Asset is not ready for attachment — confirm the upload first",
        422,
    ),

    HOLDING_MATURITY_INVALID_STATE(
        "HOLDING_MATURITY_INVALID_STATE",
        "Holding cannot be confirmed as matured in the current state",
        422,
    ),
    PENDING_ACTION_NOT_FOUND(
        "PENDING_ACTION_NOT_FOUND",
        "Pending action not found",
        404,
    ),
    PENDING_ACTION_FORBIDDEN(
        "PENDING_ACTION_FORBIDDEN",
        "You are not allowed to act on this pending action",
        403,
    ),
    PENDING_ACTION_INVALID_STATE(
        "PENDING_ACTION_INVALID_STATE",
        "Pending action is not in a state that allows this operation",
        409,
    ),
    ACCOUNT_PREFERENCES_OWNER_REQUIRED(
        "ACCOUNT_PREFERENCES_OWNER_REQUIRED",
        "Only the account owner can update account preferences",
        403,
    ),

    INVESTMENT_MANUAL_CASH_INVALID_FINANCIAL_ACCOUNT(
        "INVESTMENT_MANUAL_CASH_INVALID_FINANCIAL_ACCOUNT",
        "Manual investment cash must be recorded on an investment or benefit custodian account",
        422,
    ),
    INVESTMENT_MANUAL_CASH_ADJUSTMENT_REQUIRES_DESCRIPTION(
        "INVESTMENT_MANUAL_CASH_ADJUSTMENT_REQUIRES_DESCRIPTION",
        "Balance adjustment requires a non-empty description",
        422,
    ),
    INVESTMENT_MANUAL_CASH_SYSTEM_CATEGORY_MISSING(
        "INVESTMENT_MANUAL_CASH_SYSTEM_CATEGORY_MISSING",
        "A required system transaction category is missing",
        500,
    ),
    INVESTMENT_MANUAL_CASH_INVALID_PEER_ACCOUNT(
        "INVESTMENT_MANUAL_CASH_INVALID_PEER_ACCOUNT",
        "Peer account must be a checking/savings-style bank account in the same tenant",
        422,
    ),
    INVESTMENT_MANUAL_CASH_PEER_NOT_ALLOWED_FOR_ADJUSTMENT(
        "INVESTMENT_MANUAL_CASH_PEER_NOT_ALLOWED_FOR_ADJUSTMENT",
        "Peer account and balance adjustment cannot be combined",
        422,
    ),
    INVESTMENT_MANUAL_CASH_DATE_MUST_NOT_BE_IN_FUTURE(
        "INVESTMENT_MANUAL_CASH_DATE_MUST_NOT_BE_IN_FUTURE",
        "Manual investment cash transaction date cannot be after today",
        422,
    ),
    INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_FOUND(
        "INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_FOUND",
        "Investment cash ledger transaction not found",
        404,
    ),
    INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_EDITABLE(
        "INVESTMENT_MANUAL_CASH_TRANSACTION_NOT_EDITABLE",
        "Only manual cash movements on investment or benefit custodian accounts can be edited or removed here",
        422,
    ),
    TRANSFER_CUSTODY_HOLDING_NOT_FOUND(
        "TRANSFER_CUSTODY_HOLDING_NOT_FOUND",
        "Source holding not found",
        404,
    ),
    TRANSFER_CUSTODY_ASSET_NOT_FOUND(
        "TRANSFER_CUSTODY_ASSET_NOT_FOUND",
        "Asset for source holding not found",
        404,
    ),
    TRANSFER_CUSTODY_DESTINATION_ACCOUNT_INVALID(
        "TRANSFER_CUSTODY_DESTINATION_ACCOUNT_INVALID",
        "Destination financial account not found or not owned by account",
        422,
    ),
    TRANSFER_CUSTODY_SAME_ACCOUNT_NOT_ALLOWED(
        "TRANSFER_CUSTODY_SAME_ACCOUNT_NOT_ALLOWED",
        "Source and destination financial accounts must differ",
        422,
    ),
    TRANSFER_CUSTODY_QUANTITY_MUST_BE_POSITIVE(
        "TRANSFER_CUSTODY_QUANTITY_MUST_BE_POSITIVE",
        "Transfer quantity must be positive",
        422,
    ),
    TRANSFER_CUSTODY_QUANTITY_EXCEEDS_SOURCE_HOLDING(
        "TRANSFER_CUSTODY_QUANTITY_EXCEEDS_SOURCE_HOLDING",
        "Transfer quantity exceeds source holding quantity",
        422,
    ),
    RECORD_STOCK_LENDING_HOLDING_NOT_FOUND(
        "RECORD_STOCK_LENDING_HOLDING_NOT_FOUND",
        "Holding not found for stock lending record",
        404,
    ),
    RECORD_STOCK_LENDING_ASSET_NOT_FOUND(
        "RECORD_STOCK_LENDING_ASSET_NOT_FOUND",
        "Asset not found for stock lending record",
        404,
    ),
    RECORD_STOCK_LENDING_ASSET_TYPE_NOT_SUPPORTED(
        "RECORD_STOCK_LENDING_ASSET_TYPE_NOT_SUPPORTED",
        "Asset type does not support stock lending",
        422,
    ),
    RECORD_STOCK_LENDING_GROSS_VALUE_MUST_BE_POSITIVE(
        "RECORD_STOCK_LENDING_GROSS_VALUE_MUST_BE_POSITIVE",
        "Gross value must be positive",
        422,
    ),
    RECORD_STOCK_LENDING_NET_VALUE_MUST_BE_NON_NEGATIVE(
        "RECORD_STOCK_LENDING_NET_VALUE_MUST_BE_NON_NEGATIVE",
        "Net value must be non-negative",
        422,
    ),
    RECORD_STOCK_LENDING_INCOME_TAX_MUST_BE_NON_NEGATIVE(
        "RECORD_STOCK_LENDING_INCOME_TAX_MUST_BE_NON_NEGATIVE",
        "Income tax must be non-negative",
        422,
    ),
    RECORD_STOCK_LENDING_NET_VALUE_EXCEEDS_GROSS_VALUE(
        "RECORD_STOCK_LENDING_NET_VALUE_EXCEEDS_GROSS_VALUE",
        "Net value must be less than or equal to gross value",
        422,
    ),

    BALANCE_ADJUSTMENT_ONLY_AMOUNT_EDITABLE(
        "BALANCE_ADJUSTMENT_ONLY_AMOUNT_EDITABLE",
        "Balance adjustment transactions can only have their amount changed",
        422,
    ),

    /** Async job: consolidated (no financial account) recalculation is not implemented. */
    PORTFOLIO_RECALCULATION_CONSOLIDATED_NOT_SUPPORTED(
        "PORTFOLIO_RECALCULATION_CONSOLIDATED_NOT_SUPPORTED",
        "Portfolio-wide performance recalculation is not available yet.",
        501,
    ),

    /**
     * Async job: unexpected failure during the day-by-day recalculation loop.
     * [errorDetails] on the stored job contains the root cause class + message for support.
     */
    PORTFOLIO_RECALCULATION_FAILED(
        "PORTFOLIO_RECALCULATION_FAILED",
        "Portfolio recalculation failed due to an unexpected error.",
        500,
    ),
}
