package io.github.salomax.neotool.assets.error

import io.github.salomax.neotool.common.error.ErrorCode

/**
 * Asset domain-specific error codes.
 * These codes are returned in GraphQL extensions.code for frontend i18n handling.
 */
enum class AssetErrorCode(
    override val code: String,
    override val defaultMessage: String,
    override val httpStatus: Int = 400,
) : ErrorCode {
    ASSET_STORAGE_UNAVAILABLE(
        code = "ASSET_STORAGE_UNAVAILABLE",
        defaultMessage = "Storage service unavailable",
        httpStatus = 503,
    ),
    ASSET_STORAGE_QUOTA_EXCEEDED(
        code = "ASSET_STORAGE_QUOTA_EXCEEDED",
        defaultMessage = "Storage quota exceeded",
        httpStatus = 429,
    ),
    ASSET_NOT_FOUND(
        code = "ASSET_NOT_FOUND",
        defaultMessage = "Asset not found",
        httpStatus = 404,
    ),
    ASSET_SIZE_EXCEEDED(
        code = "ASSET_SIZE_EXCEEDED",
        defaultMessage = "File size exceeds maximum allowed",
        httpStatus = 400,
    ),
    ASSET_FORMAT_UNSUPPORTED(
        code = "ASSET_FORMAT_UNSUPPORTED",
        defaultMessage = "File format is not supported",
        httpStatus = 400,
    ),
    ASSET_INVALID_STATE(
        code = "ASSET_INVALID_STATE",
        defaultMessage = "Asset is not in the required state for this operation",
        httpStatus = 409,
    ),
    ASSET_UPLOAD_URL_EXPIRED(
        code = "ASSET_UPLOAD_URL_EXPIRED",
        defaultMessage = "Upload URL has expired",
        httpStatus = 409,
    ),
    ASSET_UPLOAD_NOT_COMPLETED(
        code = "ASSET_UPLOAD_NOT_COMPLETED",
        defaultMessage = "Upload not completed: file not found in storage",
        httpStatus = 409,
    ),
    ASSET_UPLOAD_VERIFICATION_FAILED(
        code = "ASSET_UPLOAD_VERIFICATION_FAILED",
        defaultMessage = "Upload verification failed",
        httpStatus = 409,
    ),
    ASSET_IDEMPOTENCY_CONFLICT(
        code = "ASSET_IDEMPOTENCY_CONFLICT",
        defaultMessage = "Idempotency key conflict for asset upload",
        httpStatus = 409,
    ),
    ASSET_INVALID_ID(
        code = "ASSET_INVALID_ID",
        defaultMessage = "Invalid asset identifier",
        httpStatus = 400,
    ),
    ASSET_INPUT_REQUIRED(
        code = "ASSET_INPUT_REQUIRED",
        defaultMessage = "Required input is missing",
        httpStatus = 400,
    ),
    ASSET_ACCESS_DENIED(
        code = "ASSET_ACCESS_DENIED",
        defaultMessage = "Access denied for asset operation",
        httpStatus = 403,
    ),
    ASSET_OWNER_CONTEXT_MISSING(
        code = "ASSET_OWNER_CONTEXT_MISSING",
        defaultMessage = "Owner context is required for asset operation",
        httpStatus = 400,
    ),
}
