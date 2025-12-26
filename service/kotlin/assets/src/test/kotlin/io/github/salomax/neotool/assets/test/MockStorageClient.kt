package io.github.salomax.neotool.assets.test

import io.github.salomax.neotool.assets.storage.StorageClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock storage client for testing.
 * Stores objects in memory and tracks operations.
 */
class MockStorageClient : StorageClient {
    private val objects = ConcurrentHashMap<String, StorageClient.ObjectMetadata>()
    private val presignedUrls = ConcurrentHashMap<String, String>()

    override fun generatePresignedUploadUrl(
        storageKey: String,
        mimeType: String,
        ttlSeconds: Long,
    ): String {
        val url = "https://mock-storage.example.com/upload/$storageKey?presigned=true"
        presignedUrls[storageKey] = url
        return url
    }

    override fun generatePresignedDownloadUrl(
        storageKey: String,
        ttlSeconds: Long,
    ): String {
        return "https://mock-storage.example.com/download/$storageKey?presigned=true"
    }

    override fun objectExists(storageKey: String): Boolean {
        return objects.containsKey(storageKey)
    }

    override fun getObjectMetadata(storageKey: String): StorageClient.ObjectMetadata? {
        return objects[storageKey]
    }

    override fun deleteObject(storageKey: String): Boolean {
        return objects.remove(storageKey) != null
    }

    override fun generatePublicUrl(storageKey: String): String {
        return "https://cdn.example.com/$storageKey"
    }

    /**
     * Simulate uploading an object to storage.
     * Call this after a client would have uploaded to the presigned URL.
     */
    fun simulateUpload(
        storageKey: String,
        sizeBytes: Long,
        contentType: String? = null,
    ) {
        objects[storageKey] = StorageClient.ObjectMetadata(
            sizeBytes = sizeBytes,
            contentType = contentType,
            etag = "mock-etag-$storageKey",
        )
    }

    /**
     * Clear all stored objects (for test cleanup).
     */
    fun clear() {
        objects.clear()
        presignedUrls.clear()
    }

    /**
     * Get count of stored objects.
     */
    fun getObjectCount(): Int = objects.size
}

