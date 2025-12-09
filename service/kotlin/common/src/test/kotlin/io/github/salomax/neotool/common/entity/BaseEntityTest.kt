package io.github.salomax.neotool.common.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("BaseEntity Tests")
class BaseEntityTest {
    // Test entity implementation
    private data class TestEntity(
        override val id: UUID?,
    ) : BaseEntity<UUID?>(id) {
        constructor() : this(null)
    }

    @Nested
    @DisplayName("equals()")
    inner class EqualsTests {
        @Test
        fun `should return true for same instance`() {
            // Arrange
            val entity = TestEntity(UUID.randomUUID())

            // Act & Assert
            assertThat(entity.equals(entity)).isTrue()
        }

        @Test
        fun `should return true for entities with same ID`() {
            // Arrange
            val id = UUID.randomUUID()
            val entity1 = TestEntity(id)
            val entity2 = TestEntity(id)

            // Act & Assert
            assertThat(entity1).isEqualTo(entity2)
        }

        @Test
        fun `should return false for entities with different IDs`() {
            // Arrange
            val entity1 = TestEntity(UUID.randomUUID())
            val entity2 = TestEntity(UUID.randomUUID())

            // Act & Assert
            assertThat(entity1).isNotEqualTo(entity2)
        }

        @Test
        fun `should return false for null`() {
            // Arrange
            val entity = TestEntity(UUID.randomUUID())

            // Act & Assert
            assertThat(entity.equals(null)).isFalse()
        }

        @Test
        fun `should return false for different class`() {
            // Arrange
            val entity = TestEntity(UUID.randomUUID())
            val other = "not an entity"

            // Act & Assert
            assertThat(entity.equals(other)).isFalse()
        }

        @Test
        fun `should return true for entities with null IDs`() {
            // Arrange
            val entity1 = TestEntity(null)
            val entity2 = TestEntity(null)

            // Act & Assert
            assertThat(entity1).isEqualTo(entity2)
        }
    }

    @Nested
    @DisplayName("hashCode()")
    inner class HashCodeTests {
        @Test
        fun `should return same hashCode for entities with same ID`() {
            // Arrange
            val id = UUID.randomUUID()
            val entity1 = TestEntity(id)
            val entity2 = TestEntity(id)

            // Act & Assert
            assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode())
        }

        @Test
        fun `should return zero for entity with null ID`() {
            // Arrange
            val entity = TestEntity(null)

            // Act & Assert
            assertThat(entity.hashCode()).isEqualTo(0)
        }

        @Test
        fun `should return ID hashCode for entity with ID`() {
            // Arrange
            val id = UUID.randomUUID()
            val entity = TestEntity(id)

            // Act & Assert
            assertThat(entity.hashCode()).isEqualTo(id.hashCode())
        }
    }

    @Nested
    @DisplayName("No-arg constructor")
    inner class NoArgConstructorTests {
        @Test
        fun `should create entity with null ID using no-arg constructor`() {
            // Act
            val entity = TestEntity()

            // Assert
            assertThat(entity.id).isNull()
        }
    }
}
