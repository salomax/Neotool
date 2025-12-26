package io.github.salomax.neotool.assets.service

import io.github.salomax.neotool.assets.config.AssetConfigProperties
import io.github.salomax.neotool.assets.repository.AssetRepository
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Rate limiting service for asset uploads.
 *
 * Enforces:
 * - Uploads per hour limit
 * - Uploads per day limit
 * - Total storage quota per user
 */
@Singleton
class RateLimitService(
    private val assetConfig: AssetConfigProperties,
    private val assetRepository: AssetRepository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Check if user has exceeded rate limits.
     *
     * @param userId User ID to check
     * @param newFileSizeBytes Size of the new file being uploaded
     * @throws TooManyRequestsException if any rate limit is exceeded
     */
    fun checkRateLimits(
        userId: String,
        newFileSizeBytes: Long,
    ) {
        val config = assetConfig.getRateLimitConfig()

        checkHourlyUploadLimit(userId, config.uploadsPerHour)
        checkDailyUploadLimit(userId, config.uploadsPerDay)
        checkStorageQuota(userId, newFileSizeBytes, config.storageQuotaBytes)

        logger.debug { "Rate limits passed for user: $userId" }
    }

    /**
     * Check uploads per hour limit.
     */
    private fun checkHourlyUploadLimit(
        userId: String,
        maxUploadsPerHour: Int,
    ) {
        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
        val recentUploads = assetRepository.countByOwnerIdAndCreatedAtAfter(userId, oneHourAgo)

        if (recentUploads >= maxUploadsPerHour) {
            logger.warn {
                "Hourly upload limit exceeded for user: $userId ($recentUploads >= $maxUploadsPerHour)"
            }
            throw IllegalStateException(
                "Upload rate limit exceeded: $recentUploads uploads in the last hour. " +
                    "Maximum: $maxUploadsPerHour per hour.",
            )
        }
    }

    /**
     * Check uploads per day limit.
     */
    private fun checkDailyUploadLimit(
        userId: String,
        maxUploadsPerDay: Int,
    ) {
        val oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS)
        val recentUploads = assetRepository.countByOwnerIdAndCreatedAtAfter(userId, oneDayAgo)

        if (recentUploads >= maxUploadsPerDay) {
            logger.warn {
                "Daily upload limit exceeded for user: $userId ($recentUploads >= $maxUploadsPerDay)"
            }
            throw IllegalStateException(
                "Upload rate limit exceeded: $recentUploads uploads in the last 24 hours. " +
                    "Maximum: $maxUploadsPerDay per day.",
            )
        }
    }

    /**
     * Check storage quota.
     */
    private fun checkStorageQuota(
        userId: String,
        newFileSizeBytes: Long,
        maxStorageBytes: Long,
    ) {
        val currentUsage = assetRepository.sumFileSizeByOwnerId(userId) ?: 0L
        val projectedUsage = currentUsage + newFileSizeBytes

        if (projectedUsage > maxStorageBytes) {
            logger.warn {
                "Storage quota exceeded for user: $userId " +
                    "(current: ${currentUsage / 1024 / 1024} MB + new: ${newFileSizeBytes / 1024 / 1024} MB " +
                    "> max: ${maxStorageBytes / 1024 / 1024} MB)"
            }
            throw IllegalStateException(
                "Storage quota exceeded: ${currentUsage / 1024 / 1024} MB used + " +
                    "${newFileSizeBytes / 1024 / 1024} MB > ${maxStorageBytes / 1024 / 1024} MB limit",
            )
        }
    }
}
