package io.github.salomax.neotool.assets.domain

/**
 * Asset upload lifecycle status.
 *
 * Represents the current state of an asset in its upload and processing lifecycle.
 *
 * @property PENDING Upload URL generated, waiting for client upload
 * @property READY File uploaded and confirmed, ready for use
 * @property FAILED Upload failed or expired
 * @property DELETED Asset deleted (deprecated - assets are now hard deleted)
 */
enum class AssetStatus {
    /**
     * Upload URL generated, waiting for client upload.
     * Asset is in this state immediately after createAssetUpload mutation.
     */
    PENDING,

    /**
     * File uploaded and confirmed, ready for use.
     * Asset transitions to this state after successful upload confirmation.
     */
    READY,

    /**
     * Upload failed or expired.
     * Asset enters this state when upload expires or confirmation fails.
     */
    FAILED,

    /**
     * Asset deleted (deprecated - assets are now hard deleted).
     * This status may still exist in the database for legacy records.
     */
    DELETED,
}
