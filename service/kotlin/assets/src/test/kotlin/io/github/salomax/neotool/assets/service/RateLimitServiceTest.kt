package io.github.salomax.neotool.assets.service

import io.github.salomax.neotool.assets.config.AssetConfigProperties
import io.github.salomax.neotool.assets.config.RateLimitConfig
import io.github.salomax.neotool.assets.repository.AssetRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("RateLimitService Unit Tests")
class RateLimitServiceTest {
    private lateinit var assetConfig: AssetConfigProperties
    private lateinit var assetRepository: AssetRepository
    private lateinit var rateLimitService: RateLimitService

    @BeforeEach
    fun setUp() {
        assetConfig = mock()
        assetRepository = mock()
        rateLimitService = RateLimitService(assetConfig, assetRepository)
    }

    @Nested
    @DisplayName("checkRateLimits()")
    inner class CheckRateLimitsTests {
        @Test
        fun `should pass when all limits are within thresholds`() {
            // Arrange
            val userId = "user-123"
            val newFileSizeBytes = 1_000_000L
            val rateLimitConfig =
                RateLimitConfig(
                    uploadsPerHour = 100,
                    uploadsPerDay = 1000,
                    storageQuotaBytes = 1_000_000_000L,
                )
            whenever(assetConfig.getRateLimitConfig()).thenReturn(rateLimitConfig)
            whenever(assetRepository.countByOwnerIdAndCreatedAtAfter(any(), any())).thenReturn(0L)
            whenever(assetRepository.sumFileSizeByOwnerId(userId)).thenReturn(10_000_000L)

            // Act & Assert - should not throw
            rateLimitService.checkRateLimits(userId, newFileSizeBytes)

            // Verify
            verify(assetConfig).getRateLimitConfig()
            verify(assetRepository, times(2)).countByOwnerIdAndCreatedAtAfter(any(), any())
            verify(assetRepository).sumFileSizeByOwnerId(userId)
        }

        @Test
        fun `should reject when hourly upload limit exceeded`() {
            // Arrange
            val userId = "user-123"
            val newFileSizeBytes = 1_000_000L
            val rateLimitConfig =
                RateLimitConfig(
                    uploadsPerHour = 100,
                    uploadsPerDay = 1000,
                    storageQuotaBytes = 1_000_000_000L,
                )
            whenever(assetConfig.getRateLimitConfig()).thenReturn(rateLimitConfig)
            whenever(assetRepository.countByOwnerIdAndCreatedAtAfter(any(), any()))
                .thenReturn(100L) // At limit

            // Act & Assert
            assertThatThrownBy {
                rateLimitService.checkRateLimits(userId, newFileSizeBytes)
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Upload rate limit exceeded")
                .hasMessageContaining("100")
                .hasMessageContaining("hour")

            // Verify
            verify(assetConfig).getRateLimitConfig()
            verify(assetRepository).countByOwnerIdAndCreatedAtAfter(any(), any())
        }

        @Test
        fun `should reject when daily upload limit exceeded`() {
            // Arrange
            val userId = "user-123"
            val newFileSizeBytes = 1_000_000L
            val rateLimitConfig =
                RateLimitConfig(
                    uploadsPerHour = 100,
                    uploadsPerDay = 1000,
                    storageQuotaBytes = 1_000_000_000L,
                )
            whenever(assetConfig.getRateLimitConfig()).thenReturn(rateLimitConfig)

            // First call (hourly check) returns under limit
            // Second call (daily check) returns at limit
            whenever(assetRepository.countByOwnerIdAndCreatedAtAfter(any(), any()))
                .thenReturn(50L) // Under hourly limit
                .thenReturn(1000L) // At daily limit

            // Act & Assert
            assertThatThrownBy {
                rateLimitService.checkRateLimits(userId, newFileSizeBytes)
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Upload rate limit exceeded")
                .hasMessageContaining("1000")
                .hasMessageContaining("24 hours")

            // Verify
            verify(assetConfig).getRateLimitConfig()
        }

        @Test
        fun `should reject when storage quota exceeded`() {
            // Arrange
            val userId = "user-123"
            val newFileSizeBytes = 100_000_000L // 100 MB
            val currentUsage = 950_000_000L // 950 MB
            val rateLimitConfig =
                RateLimitConfig(
                    uploadsPerHour = 100,
                    uploadsPerDay = 1000,
                    storageQuotaBytes = 1_000_000_000L, // 1 GB limit
                )
            whenever(assetConfig.getRateLimitConfig()).thenReturn(rateLimitConfig)
            whenever(assetRepository.countByOwnerIdAndCreatedAtAfter(any(), any())).thenReturn(0L)
            whenever(assetRepository.sumFileSizeByOwnerId(userId)).thenReturn(currentUsage)

            // Act & Assert
            assertThatThrownBy {
                rateLimitService.checkRateLimits(userId, newFileSizeBytes)
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Storage quota exceeded")

            // Verify
            verify(assetConfig).getRateLimitConfig()
            verify(assetRepository).sumFileSizeByOwnerId(userId)
        }

        @Test
        fun `should pass when storage quota is at exact limit`() {
            // Arrange
            val userId = "user-123"
            val newFileSizeBytes = 50_000_000L // 50 MB
            val currentUsage = 950_000_000L // 950 MB
            val rateLimitConfig =
                RateLimitConfig(
                    uploadsPerHour = 100,
                    uploadsPerDay = 1000,
                    storageQuotaBytes = 1_000_000_000L, // 1 GB limit - exactly matches new total
                )
            whenever(assetConfig.getRateLimitConfig()).thenReturn(rateLimitConfig)
            whenever(assetRepository.countByOwnerIdAndCreatedAtAfter(any(), any())).thenReturn(0L)
            whenever(assetRepository.sumFileSizeByOwnerId(userId)).thenReturn(currentUsage)

            // Act & Assert - should not throw
            rateLimitService.checkRateLimits(userId, newFileSizeBytes)

            // Verify
            verify(assetConfig).getRateLimitConfig()
            verify(assetRepository).sumFileSizeByOwnerId(userId)
        }

        @Test
        fun `should handle zero current usage`() {
            // Arrange
            val userId = "new-user-123"
            val newFileSizeBytes = 1_000_000L
            val rateLimitConfig =
                RateLimitConfig(
                    uploadsPerHour = 100,
                    uploadsPerDay = 1000,
                    storageQuotaBytes = 1_000_000_000L,
                )
            whenever(assetConfig.getRateLimitConfig()).thenReturn(rateLimitConfig)
            whenever(assetRepository.countByOwnerIdAndCreatedAtAfter(any(), any())).thenReturn(0L)
            whenever(assetRepository.sumFileSizeByOwnerId(userId)).thenReturn(null) // No existing assets

            // Act & Assert - should not throw
            rateLimitService.checkRateLimits(userId, newFileSizeBytes)

            // Verify
            verify(assetRepository).sumFileSizeByOwnerId(userId)
        }

        @Test
        fun `should reject when exactly at hourly limit`() {
            // Arrange
            val userId = "user-123"
            val newFileSizeBytes = 1_000_000L
            val rateLimitConfig =
                RateLimitConfig(
                    uploadsPerHour = 50,
                    uploadsPerDay = 1000,
                    storageQuotaBytes = 1_000_000_000L,
                )
            whenever(assetConfig.getRateLimitConfig()).thenReturn(rateLimitConfig)
            whenever(assetRepository.countByOwnerIdAndCreatedAtAfter(any(), any())).thenReturn(50L)

            // Act & Assert
            assertThatThrownBy {
                rateLimitService.checkRateLimits(userId, newFileSizeBytes)
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("50")

            // Verify
            verify(assetRepository).countByOwnerIdAndCreatedAtAfter(any(), any())
        }

        @Test
        fun `should pass when one upload under hourly limit`() {
            // Arrange
            val userId = "user-123"
            val newFileSizeBytes = 1_000_000L
            val rateLimitConfig =
                RateLimitConfig(
                    uploadsPerHour = 50,
                    uploadsPerDay = 1000,
                    storageQuotaBytes = 1_000_000_000L,
                )
            whenever(assetConfig.getRateLimitConfig()).thenReturn(rateLimitConfig)
            whenever(assetRepository.countByOwnerIdAndCreatedAtAfter(any(), any())).thenReturn(49L)
            whenever(assetRepository.sumFileSizeByOwnerId(userId)).thenReturn(0L)

            // Act & Assert - should not throw
            rateLimitService.checkRateLimits(userId, newFileSizeBytes)

            // Verify
            verify(assetRepository, times(2)).countByOwnerIdAndCreatedAtAfter(any(), any())
        }
    }
}
