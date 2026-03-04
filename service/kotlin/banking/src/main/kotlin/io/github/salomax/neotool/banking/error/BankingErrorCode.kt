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
    IMPORT_FILE_FORMAT_NOT_SUPPORTED(
        "IMPORT_FILE_FORMAT_NOT_SUPPORTED",
        "Import file format is not supported",
        422,
    ),
    TRANSACTION_BEFORE_INITIAL_BALANCE(
        "TRANSACTION_BEFORE_INITIAL_BALANCE",
        "Transaction date is before the account's initial balance date",
        422,
    ),
}
