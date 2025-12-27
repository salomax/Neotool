package io.github.salomax.neotool.security.test.repository

import io.github.salomax.neotool.security.domain.rbac.Group
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.domain.rbac.User
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("Entity Conversion Tests")
class EntityConversionTest {
    @Nested
    @DisplayName("User Entity Conversion")
    inner class UserEntityConversionTests {
        @Test
        fun `toEntity should pass null ID when domain id is null`() {
            // Arrange
            val domain =
                User(
                    id = null,
                    email = "test@example.com",
                    displayName = "Test User",
                    createdAt = Instant.now(),
                )

            // Act
            val entity = domain.toEntity()

            // Assert
            assertThat(entity.id).isNull()
            assertThat(entity.email).isEqualTo("test@example.com")
            assertThat(entity.displayName).isEqualTo("Test User")
        }

        @Test
        fun `toEntity should preserve existing UUID when domain has id`() {
            // Arrange
            val existingId = UUID.randomUUID()
            val domain =
                User(
                    id = existingId,
                    email = "test@example.com",
                    displayName = "Test User",
                    createdAt = Instant.now(),
                )

            // Act
            val entity = domain.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(existingId)
            assertThat(entity.email).isEqualTo("test@example.com")
        }

        @Test
        fun `toDomain should convert entity to domain with all fields`() {
            // Arrange
            val entity =
                SecurityTestDataBuilders.user(
                    id = UUID.randomUUID(),
                    email = "test@example.com",
                    displayName = "Test User",
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(entity.id)
            assertThat(domain.email).isEqualTo(entity.email)
            assertThat(domain.displayName).isEqualTo(entity.displayName)
            assertThat(domain.createdAt).isEqualTo(entity.createdAt)
            // Note: enabled is now stored in Principal, not User
        }
    }

    @Nested
    @DisplayName("Group Entity Conversion")
    inner class GroupEntityConversionTests {
        @Test
        fun `toEntity should generate UUID when domain id is null`() {
            // Arrange
            val domain =
                Group(
                    id = null,
                    name = "Test Group",
                    description = "Test Description",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val entity = domain.toEntity()

            // Assert
            assertThat(entity.id).isNotNull()
            assertThat(entity.name).isEqualTo("Test Group")
            assertThat(entity.description).isEqualTo("Test Description")
            // Note: Currently generates UUID v4, but should ideally pass null for database to generate UUID v7
        }

        @Test
        fun `toEntity should preserve existing UUID when domain has id`() {
            // Arrange
            val existingId = UUID.randomUUID()
            val domain =
                Group(
                    id = existingId,
                    name = "Test Group",
                    description = "Test Description",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val entity = domain.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(existingId)
            assertThat(entity.name).isEqualTo("Test Group")
        }

        @Test
        fun `toDomain should convert entity to domain with all fields`() {
            // Arrange
            val entity =
                SecurityTestDataBuilders.group(
                    id = UUID.randomUUID(),
                    name = "Test Group",
                    description = "Test Description",
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(entity.id)
            assertThat(domain.name).isEqualTo(entity.name)
            assertThat(domain.description).isEqualTo(entity.description)
            assertThat(domain.createdAt).isEqualTo(entity.createdAt)
            assertThat(domain.updatedAt).isEqualTo(entity.updatedAt)
            assertThat(domain.version).isEqualTo(entity.version)
        }
    }

    @Nested
    @DisplayName("Role Entity Conversion")
    inner class RoleEntityConversionTests {
        @Test
        fun `toEntity should pass null ID when domain id is null`() {
            // Arrange
            val domain =
                Role(
                    id = null,
                    name = "Test Role",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val entity = domain.toEntity()

            // Assert
            assertThat(entity.id).isNull()
            assertThat(entity.name).isEqualTo("Test Role")
        }

        @Test
        fun `toEntity should preserve existing ID when domain has id`() {
            // Arrange
            val existingId = UUID.randomUUID()
            val domain =
                Role(
                    id = existingId,
                    name = "Test Role",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val entity = domain.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(existingId)
            assertThat(entity.name).isEqualTo("Test Role")
        }

        @Test
        fun `toDomain should convert entity to domain with all fields`() {
            // Arrange
            val entity =
                SecurityTestDataBuilders.role(
                    id = UUID.randomUUID(),
                    name = "Test Role",
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(entity.id)
            assertThat(domain.name).isEqualTo(entity.name)
            assertThat(domain.createdAt).isEqualTo(entity.createdAt)
            assertThat(domain.updatedAt).isEqualTo(entity.updatedAt)
            assertThat(domain.version).isEqualTo(entity.version)
        }
    }

    @Nested
    @DisplayName("Permission Entity Conversion")
    inner class PermissionEntityConversionTests {
        @Test
        fun `toEntity should pass null ID when domain id is null`() {
            // Arrange
            val domain =
                Permission(
                    id = null,
                    name = "permission:test",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val entity = domain.toEntity()

            // Assert
            assertThat(entity.id).isNull()
            assertThat(entity.name).isEqualTo("permission:test")
        }

        @Test
        fun `toEntity should preserve existing ID when domain has id`() {
            // Arrange
            val existingId = UUID.randomUUID()
            val domain =
                Permission(
                    id = existingId,
                    name = "permission:test",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            // Act
            val entity = domain.toEntity()

            // Assert
            assertThat(entity.id).isEqualTo(existingId)
            assertThat(entity.name).isEqualTo("permission:test")
        }

        @Test
        fun `toDomain should convert entity to domain with all fields`() {
            // Arrange
            val entity =
                SecurityTestDataBuilders.permission(
                    id = UUID.randomUUID(),
                    name = "permission:test",
                )

            // Act
            val domain = entity.toDomain()

            // Assert
            assertThat(domain.id).isEqualTo(entity.id)
            assertThat(domain.name).isEqualTo(entity.name)
            assertThat(domain.createdAt).isEqualTo(entity.createdAt)
            assertThat(domain.updatedAt).isEqualTo(entity.updatedAt)
            assertThat(domain.version).isEqualTo(entity.version)
        }
    }
}
