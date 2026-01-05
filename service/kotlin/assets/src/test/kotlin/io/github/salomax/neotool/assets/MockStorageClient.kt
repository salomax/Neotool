package io.github.salomax.neotool.assets.test

import io.github.salomax.neotool.assets.storage.StorageClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock storage client for testing.
 * Stores objects in memory and tracks operations per bucket.
 */
class MockStorageClient : StorageClient {
    // Track objects per bucket: bucket -> key -> metadata
    private val objects = ConcurrentHashMap<String, ConcurrentHashMap<String, StorageClient.ObjectMetadata>>()
    private val presignedUrls = ConcurrentHashMap<String, String>()

    override fun generatePresignedUploadUrl(
        bucket: String,
        storageKey: String,
        mimeType: String,
        ttlSeconds: Long,
    ): String {
        val url = "https://mock-storage.example.com/$bucket/upload/$storageKey?presigned=true"
        presignedUrls["$bucket/$storageKey"] = url
        return url
    }

    override fun generatePresignedDownloadUrl(
        bucket: String,
        storageKey: String,
        ttlSeconds: Long,
    ): String {
        return "https://mock-storage.example.com/$bucket/download/$storageKey?presigned=true"
    }

    override fun objectExists(
        bucket: String,
        storageKey: String,
    ): Boolean {
        return objects[bucket]?.containsKey(storageKey) ?: false
    }

    override fun getObjectMetadata(
        bucket: String,
        storageKey: String,
    ): StorageClient.ObjectMetadata? {
        return objects[bucket]?.get(storageKey)
    }

    override fun deleteObject(
        bucket: String,
        storageKey: String,
    ): Boolean {
        return objects[bucket]?.remove(storageKey) != null
    }

    override fun generatePublicUrl(
        bucket: String,
        storageKey: String,
    ): String {
        return "https://cdn.example.com/$bucket/$storageKey"
    }

    /**
     * Simulate uploading an object to storage.
     * Call this after a client would have uploaded to the presigned URL.
     */
    fun simulateUpload(
        bucket: String,
        storageKey: String,
        sizeBytes: Long,
        contentType: String? = null,
    ) {
        objects.computeIfAbsent(bucket) { ConcurrentHashMap() }[storageKey] =
            StorageClient.ObjectMetadata(
                sizeBytes = sizeBytes,
                contentType = contentType,
                etag = "mock-etag-$bucket-$storageKey",
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
     * Get count of stored objects across all buckets.
     */
    fun getObjectCount(): Int = objects.values.sumOf { it.size }

    /**
     * Get count of stored objects in a specific bucket.
     */
    fun getObjectCount(bucket: String): Int = objects[bucket]?.size ?: 0
}
