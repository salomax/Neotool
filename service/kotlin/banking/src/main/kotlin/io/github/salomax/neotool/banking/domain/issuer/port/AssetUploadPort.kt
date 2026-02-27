package io.github.salomax.neotool.banking.domain.issuer.port

/**
 * Port for uploading assets to public storage (bucket/CDN).
 *
 * Implementation is provided by the infrastructure layer.
 * In production this calls the assets service (S3/R2).
 * During development or when the assets service is unavailable,
 * a stub can be used.
 */
interface AssetUploadPort {
    /**
     * Upload raw bytes to public storage.
     *
     * @param key filename / storage hint, e.g. "issuers/abc123.png"
     * @param bytes raw content
     * @param contentType MIME type, e.g. "image/png"
     * @return opaque asset identifier returned by the storage backend (asset UUID when using the assets service)
     */
    fun upload(key: String, bytes: ByteArray, contentType: String): String
}
