package io.github.salomax.neotool.example.test.unit.graphql.mapper

import io.github.salomax.neotool.example.graphql.dto.ProductInputDTO
import io.github.salomax.neotool.example.graphql.mapper.ProductGraphQLMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("ProductGraphQLMapper Unit Tests")
class ProductGraphQLMapperTest {
    private lateinit var mapper: ProductGraphQLMapper

    @BeforeEach
    fun setUp() {
        mapper = ProductGraphQLMapper()
    }

    @Nested
    @DisplayName("extractField()")
    inner class ExtractFieldTests {
        @Test
        fun `should return value when field exists and type matches`() {
            // Arrange
            val input = mapOf("name" to "Test Product")

            // Act
            val result: String = mapper.extractField(input, "name")

            // Assert
            assertThat(result).isEqualTo("Test Product")
        }

        @Test
        fun `should return default when field is missing and default provided`() {
            // Arrange
            val input = mapOf<String, Any?>()

            // Act
            val result: Long = mapper.extractField(input, "priceCents", 0L)

            // Assert
            assertThat(result).isEqualTo(0L)
        }

        @Test
        fun `should return default when field is null and default provided`() {
            // Arrange
            val input = mapOf("priceCents" to null)

            // Act
            val result: Long = mapper.extractField(input, "priceCents", 0L)

            // Assert
            assertThat(result).isEqualTo(0L)
        }

        @Test
        fun `should throw when field is missing and no default provided`() {
            // Arrange
            val input = mapOf<String, Any?>()

            // Act & Assert
            assertThatThrownBy {
                mapper.extractField<String>(input, "name")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Field 'name' is required")
        }

        @Test
        fun `should throw when field is null and no default provided`() {
            // Arrange
            val input = mapOf("name" to null)

            // Act & Assert
            assertThatThrownBy {
                mapper.extractField<String>(input, "name")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Field 'name' is required")
        }

        @Test
        fun `should return default when type cast fails and default provided`() {
            // Arrange - wrong type, cast will return null
            val input = mapOf("priceCents" to "not-a-number")

            // Act
            val result: Long = mapper.extractField(input, "priceCents", 0L)

            // Assert - should use default when cast fails (returns null)
            assertThat(result).isEqualTo(0L)
        }

        @Test
        fun `should throw when type cast fails and no default provided`() {
            // Arrange - wrong type, cast will return null
            val input = mapOf("name" to 12345)

            // Act & Assert
            assertThatThrownBy {
                mapper.extractField<String>(input, "name")
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Field 'name' is required")
        }
    }

    @Nested
    @DisplayName("mapToInputDTO()")
    inner class MapToInputDTOTests {
        @Test
        fun `should map input with all fields provided`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Product",
                    "sku" to "TEST-001",
                    "priceCents" to 9999L,
                    "stock" to 10,
                )

            // Act
            val result = mapper.mapToInputDTO(input)

            // Assert
            assertThat(result.name).isEqualTo("Test Product")
            assertThat(result.sku).isEqualTo("TEST-001")
            assertThat(result.priceCents).isEqualTo(9999L)
            assertThat(result.stock).isEqualTo(10)
        }

        @Test
        fun `should use default values for optional fields`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Product",
                    "sku" to "TEST-001",
                    // priceCents and stock missing, should use defaults
                )

            // Act
            val result = mapper.mapToInputDTO(input)

            // Assert
            assertThat(result.name).isEqualTo("Test Product")
            assertThat(result.sku).isEqualTo("TEST-001")
            assertThat(result.priceCents).isEqualTo(0L) // Default
            assertThat(result.stock).isEqualTo(0) // Default
        }

        @Test
        fun `should throw when required field name is missing`() {
            // Arrange
            val input =
                mapOf(
                    "sku" to "TEST-001",
                    // name is missing
                )

            // Act & Assert
            assertThatThrownBy {
                mapper.mapToInputDTO(input)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Field 'name' is required")
        }

        @Test
        fun `should throw when required field sku is missing`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Product",
                    // sku is missing
                )

            // Act & Assert
            assertThatThrownBy {
                mapper.mapToInputDTO(input)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Field 'sku' is required")
        }

        @Test
        fun `should use default when optional field is null`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Product",
                    "sku" to "TEST-001",
                    "priceCents" to null,
                    "stock" to null,
                )

            // Act
            val result = mapper.mapToInputDTO(input)

            // Assert
            assertThat(result.priceCents).isEqualTo(0L) // Default used
            assertThat(result.stock).isEqualTo(0) // Default used
        }
    }

    @Nested
    @DisplayName("mapToEntity()")
    inner class MapToEntityTests {
        @Test
        fun `should map DTO to entity for create (no id)`() {
            // Arrange
            val dto =
                ProductInputDTO(
                    name = "Test Product",
                    sku = "TEST-001",
                    priceCents = 9999L,
                    stock = 10,
                )

            // Act
            val result = mapper.mapToEntity(dto)

            // Assert
            assertThat(result.id).isNull()
            assertThat(result.name).isEqualTo("Test Product")
            assertThat(result.sku).isEqualTo("TEST-001")
            assertThat(result.priceCents).isEqualTo(9999L)
            assertThat(result.stock).isEqualTo(10)
        }

        @Test
        fun `should map DTO to entity for update (with id)`() {
            // Arrange
            val id = UUID.randomUUID()
            val dto =
                ProductInputDTO(
                    name = "Updated Product",
                    sku = "UPDATED-001",
                    priceCents = 19999L,
                    stock = 20,
                )

            // Act
            val result = mapper.mapToEntity(dto, id)

            // Assert
            assertThat(result.id).isEqualTo(id)
            assertThat(result.name).isEqualTo("Updated Product")
            assertThat(result.sku).isEqualTo("UPDATED-001")
            assertThat(result.priceCents).isEqualTo(19999L)
            assertThat(result.stock).isEqualTo(20)
        }
    }
}
