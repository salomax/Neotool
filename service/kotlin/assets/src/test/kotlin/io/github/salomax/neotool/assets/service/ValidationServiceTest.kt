package io.github.salomax.neotool.assets.service

import io.github.salomax.neotool.assets.config.AssetConfigProperties
import io.github.salomax.neotool.assets.domain.AssetResourceType
import io.github.salomax.neotool.assets.test.ValidationTestDataBuilders
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("ValidationService Unit Tests")
class ValidationServiceTest {
    private lateinit var assetConfig: AssetConfigProperties
    private lateinit var validationService: ValidationService

    @BeforeEach
    fun setUp() {
        assetConfig = mock()
        validationService = ValidationService(assetConfig)
    }

    @Nested
    @DisplayName("validateMimeType()")
    inner class ValidateMimeTypeTests {
        @Test
        fun `should accept valid MIME type for namespace`() {
            // Arrange
            val namespace = "user-profiles"
            val mimeType = "image/jpeg"
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert - should not throw
            validationService.validateMimeType(namespace, mimeType, AssetResourceType.PROFILE_IMAGE)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should accept MIME type case-insensitively`() {
            // Arrange
            val namespace = "user-profiles"
            val mimeType = "IMAGE/JPEG"
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert - should not throw
            validationService.validateMimeType(namespace, mimeType, AssetResourceType.PROFILE_IMAGE)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should reject invalid MIME type for namespace`() {
            // Arrange
            val namespace = "user-profiles"
            val mimeType = "application/pdf"
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert
            assertThatThrownBy {
                validationService.validateMimeType(namespace, mimeType, AssetResourceType.PROFILE_IMAGE)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid MIME type")
                .hasMessageContaining(mimeType)
                .hasMessageContaining(namespace)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should accept MIME type when namespace returns default config`() {
            // Arrange
            val namespace = "unknown-namespace"
            val mimeType = "image/jpeg"
            val defaultConfig = ValidationTestDataBuilders.defaultNamespaceConfig()
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(defaultConfig)

            // Act & Assert - should not throw
            validationService.validateMimeType(namespace, mimeType, AssetResourceType.ATTACHMENT)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should reject empty MIME type`() {
            // Arrange
            val namespace = "user-profiles"
            val mimeType = ""
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert
            assertThatThrownBy {
                validationService.validateMimeType(namespace, mimeType, AssetResourceType.PROFILE_IMAGE)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid MIME type")
                .hasMessageContaining(mimeType)
                .hasMessageContaining(namespace)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @ParameterizedTest
        @ValueSource(strings = ["image/jpeg", "image/png", "image/webp", "image/gif"])
        fun `should accept various valid MIME types`(mimeType: String) {
            // Arrange
            val namespace = "user-profiles"
            val namespaceConfig =
                ValidationTestDataBuilders.namespaceConfig(
                    name = namespace,
                    allowedMimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif"),
                )
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert - should not throw
            validationService.validateMimeType(namespace, mimeType, AssetResourceType.PROFILE_IMAGE)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should validate for different resource types`() {
            // Arrange
            val namespace = "group-assets"
            val mimeType = "image/png"
            val namespaceConfig =
                ValidationTestDataBuilders.namespaceConfig(
                    name = namespace,
                    resourceTypes = setOf(AssetResourceType.GROUP_LOGO, AssetResourceType.GROUP_BANNER),
                )
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert - should not throw (resourceType parameter doesn't affect validation currently)
            validationService.validateMimeType(namespace, mimeType, AssetResourceType.GROUP_LOGO)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }
    }

    @Nested
    @DisplayName("validateFileSize()")
    inner class ValidateFileSizeTests {
        @Test
        fun `should accept valid file size for namespace`() {
            // Arrange
            val namespace = "user-profiles"
            val sizeBytes = 5_000_000L // 5 MB
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert - should not throw
            validationService.validateFileSize(namespace, sizeBytes, AssetResourceType.PROFILE_IMAGE)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should accept file at exact size limit`() {
            // Arrange
            val namespace = "user-profiles"
            val sizeBytes = 10_000_000L // Exactly 10 MB
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert - should not throw
            validationService.validateFileSize(namespace, sizeBytes, AssetResourceType.PROFILE_IMAGE)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should reject file exceeding size limit`() {
            // Arrange
            val namespace = "user-profiles"
            val sizeBytes = 15_000_000L // 15 MB
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert
            assertThatThrownBy {
                validationService.validateFileSize(namespace, sizeBytes, AssetResourceType.PROFILE_IMAGE)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("File size exceeds limit")
                .hasMessageContaining(namespace)

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should reject zero file size`() {
            // Arrange
            val namespace = "user-profiles"
            val sizeBytes = 0L

            // Act & Assert
            assertThatThrownBy {
                validationService.validateFileSize(namespace, sizeBytes, AssetResourceType.PROFILE_IMAGE)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("File size must be greater than 0")

            // Verify - getNamespaceConfig is not called because validation fails before it's needed
            verify(assetConfig, never()).getNamespaceConfig(any())
        }

        @Test
        fun `should reject negative file size`() {
            // Arrange
            val namespace = "user-profiles"
            val sizeBytes = -1L

            // Act & Assert
            assertThatThrownBy {
                validationService.validateFileSize(namespace, sizeBytes, AssetResourceType.PROFILE_IMAGE)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("File size must be greater than 0")

            // Verify - getNamespaceConfig is not called because validation fails before it's needed
            verify(assetConfig, never()).getNamespaceConfig(any())
        }
    }

    @Nested
    @DisplayName("validate()")
    inner class ValidateTests {
        @Test
        fun `should validate both MIME type and file size successfully`() {
            // Arrange
            val namespace = "user-profiles"
            val mimeType = "image/jpeg"
            val sizeBytes = 5_000_000L
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert - should not throw
            validationService.validate(namespace, mimeType, sizeBytes, AssetResourceType.PROFILE_IMAGE)

            // Verify - validate() calls both validateMimeType() and validateFileSize(), each calling getNamespaceConfig()
            verify(assetConfig, times(2)).getNamespaceConfig(namespace)
        }

        @Test
        fun `should fail when MIME type is invalid`() {
            // Arrange
            val namespace = "user-profiles"
            val mimeType = "application/pdf"
            val sizeBytes = 5_000_000L
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert
            assertThatThrownBy {
                validationService.validate(namespace, mimeType, sizeBytes, AssetResourceType.PROFILE_IMAGE)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid MIME type")

            // Verify
            verify(assetConfig).getNamespaceConfig(namespace)
        }

        @Test
        fun `should fail when file size exceeds limit`() {
            // Arrange
            val namespace = "user-profiles"
            val mimeType = "image/jpeg"
            val sizeBytes = 15_000_000L
            val namespaceConfig = ValidationTestDataBuilders.namespaceConfig(name = namespace)
            whenever(assetConfig.getNamespaceConfig(namespace)).thenReturn(namespaceConfig)

            // Act & Assert
            assertThatThrownBy {
                validationService.validate(namespace, mimeType, sizeBytes, AssetResourceType.PROFILE_IMAGE)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("File size exceeds limit")

            // Verify - validate() calls validateMimeType() first (which succeeds), then validateFileSize() (which fails)
            verify(assetConfig, times(2)).getNamespaceConfig(namespace)
        }
    }
}
