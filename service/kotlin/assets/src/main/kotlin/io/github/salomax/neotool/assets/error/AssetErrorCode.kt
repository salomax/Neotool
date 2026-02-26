package io.github.salomax.neotool.assets.error

import io.github.salomax.neotool.common.error.ErrorCode

/**
 * Asset domain error codes sent to frontend for i18n translation.
 */
enum class AssetErrorCode(
    override val code: String,
    override val defaultMessage: String,
    override val httpStatus: Int = 400,
) : ErrorCode {
    ASSET_STORAGE_UNAVAILABLE("ASSET_STORAGE_UNAVAILABLE", "Storage service unavailable", 503),
    ASSET_STORAGE_QUOTA_EXCEEDED("ASSET_STORAGE_QUOTA_EXCEEDED", "Storage quota exceeded", 429),
    ASSET_NOT_FOUND("ASSET_NOT_FOUND", "Asset not found", 404),
    ASSET_ACCESS_DENIED("ASSET_ACCESS_DENIED", "Access to this asset is denied", 403),
    ASSET_INVALID_STATE("ASSET_INVALID_STATE", "Asset is not in a valid state for this operation", 409),
    ASSET_UPLOAD_EXPIRED("ASSET_UPLOAD_EXPIRED", "Upload URL has expired", 409),
    ASSET_UPLOAD_NOT_COMPLETED("ASSET_UPLOAD_NOT_COMPLETED", "Upload not completed", 409),
    ASSET_UPLOAD_VERIFICATION_FAILED("ASSET_UPLOAD_VERIFICATION_FAILED", "Upload verification failed", 409),
    ASSET_FORMAT_UNSUPPORTED("ASSET_FORMAT_UNSUPPORTED", "File format is not supported", 400),
    ASSET_SIZE_EXCEEDED("ASSET_SIZE_EXCEEDED", "File size exceeds allowed limit", 400),
    ASSET_SIZE_INVALID("ASSET_SIZE_INVALID", "File size must be greater than zero", 400),
    ASSET_IDEMPOTENCY_CONFLICT("ASSET_IDEMPOTENCY_CONFLICT", "Idempotency key conflicts with current request", 409),
    ASSET_INVALID_ID("ASSET_INVALID_ID", "Invalid asset identifier", 400),
    ASSET_INPUT_REQUIRED("ASSET_INPUT_REQUIRED", "Required input is missing", 400),
}
