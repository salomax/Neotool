package io.github.salomax.neotool.banking.client.assets

import java.util.UUID

/**
 * Port for temporary import file storage.
 * Files are stored for the duration of async processing (max 24h via download URL TTL).
 * Implementation delegates to the assets service.
 */
interface ImportFileStoragePort {
    /**
     * Stores the file and returns an [assetId] that can be used to retrieve it later.
     *
     * @param accountId tenant ID used as ownerId in the assets service
     * @param bytes raw file bytes
     * @param fileName original filename (for display only)
     * @param mimeType file MIME type
     * @return assetId (UUID) — store in ImportJob for the consumer to use
     */
    fun store(
        accountId: UUID,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): UUID

    /**
     * Retrieves the file bytes using a presigned download URL from the assets service.
     */
    fun read(
        accountId: UUID,
        assetId: UUID,
    ): ByteArray

    /**
     * Resolves a presigned download URL for an existing asset.
     * Useful when downstream processors need to pull the file directly (e.g. pdf-extractor).
     */
    fun resolveDownloadUrl(
        accountId: UUID,
        assetId: UUID,
        ttlSeconds: Int = 3600,
    ): String

    /**
     * Deletes the asset from the assets service (best-effort; idempotent).
     */
    fun delete(
        accountId: UUID,
        assetId: UUID,
    )
}
