package io.github.salomax.neotool.assets.error

import io.github.salomax.neotool.common.error.ErrorCode

/**
 * Asset domain error codes.
 * These codes are sent to the frontend for i18n translation.
 */
enum class AssetErrorCode(
    override val code: String,
    override val defaultMessage: String,
    override val httpStatus: Int = 400,
) : ErrorCode {
    // Storage errors
    STORAGE_UNAVAILABLE("ASSET_STORAGE_UNAVAILABLE", "Storage service is temporarily unavailable", 503),
    STORAGE_QUOTA_EXCEEDED("ASSET_STORAGE_QUOTA_EXCEEDED", "Storage quota exceeded", 507),
    STORAGE_WRITE_FAILED("ASSET_STORAGE_WRITE_FAILED", "Failed to write to storage", 500),
    STORAGE_READ_FAILED("ASSET_STORAGE_READ_FAILED", "Failed to read from storage", 500),

    // Asset validation errors
    ASSET_NOT_FOUND("ASSET_NOT_FOUND", "Asset not found", 404),
    ASSET_NAME_REQUIRED("ASSET_NAME_REQUIRED", "Asset name is required", 400),
    ASSET_TYPE_INVALID("ASSET_TYPE_INVALID", "Invalid asset type", 400),
    ASSET_SIZE_EXCEEDED("ASSET_SIZE_EXCEEDED", "Asset size exceeds maximum allowed size", 413),
    ASSET_FORMAT_UNSUPPORTED("ASSET_FORMAT_UNSUPPORTED", "Asset format is not supported", 415),

    // Asset access errors
    ASSET_ACCESS_DENIED("ASSET_ACCESS_DENIED", "Access to this asset is denied", 403),
    ASSET_UPLOAD_FAILED("ASSET_UPLOAD_FAILED", "Asset upload failed", 500),
    ASSET_DOWNLOAD_FAILED("ASSET_DOWNLOAD_FAILED", "Asset download failed", 500),
    ASSET_DELETE_FAILED("ASSET_DELETE_FAILED", "Asset deletion failed", 500),

    // Asset state errors
    ASSET_ALREADY_EXISTS("ASSET_ALREADY_EXISTS", "Asset already exists", 409),
    ASSET_IN_USE("ASSET_IN_USE", "Asset is currently in use and cannot be deleted", 409),
    ASSET_CORRUPTED("ASSET_CORRUPTED", "Asset is corrupted", 422),
}
