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
    IMPORT_OFX_DESTINATION_MISMATCH(
        "IMPORT_OFX_DESTINATION_MISMATCH",
        "OFX statement type is not compatible with the destination financial account",
        422,
    ),
    TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW(
        "TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW",
        "Transaction date must be within the last 5 years",
        422,
    ),
    TRANSACTION_BEFORE_INITIAL_BALANCE(
        "TRANSACTION_BEFORE_INITIAL_BALANCE",
        "Transaction date is before the account's initial balance date",
        422,
    ),
}
