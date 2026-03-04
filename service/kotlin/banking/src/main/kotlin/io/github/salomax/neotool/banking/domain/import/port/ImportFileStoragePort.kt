package io.github.salomax.neotool.banking.domain.import.port

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
    fun store(accountId: UUID, bytes: ByteArray, fileName: String, mimeType: String): UUID

    /**
     * Retrieves the file bytes using a presigned download URL from the assets service.
     */
    fun read(assetId: UUID): ByteArray

    /**
     * Deletes the asset from the assets service (best-effort; idempotent).
     */
    fun delete(assetId: UUID)
}
