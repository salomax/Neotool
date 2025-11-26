package io.github.salomax.neotool.example.test.unit.graphql.mapper

import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.CustomerStatus
import io.github.salomax.neotool.example.graphql.dto.CustomerInputDTO
import io.github.salomax.neotool.example.graphql.mapper.CustomerGraphQLMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("CustomerGraphQLMapper Unit Tests")
class CustomerGraphQLMapperTest {
    private lateinit var mapper: CustomerGraphQLMapper

    @BeforeEach
    fun setUp() {
        // Create mapper with a function that returns null (for create scenarios)
        mapper = CustomerGraphQLMapper { null }
    }

    @Nested
    @DisplayName("extractField()")
    inner class ExtractFieldTests {
        @Test
        fun `should return value when field exists and type matches`() {
            // Arrange
            val input = mapOf("name" to "Test Customer")

            // Act
            val result: String = mapper.extractField(input, "name")

            // Assert
            assertThat(result).isEqualTo("Test Customer")
        }

        @Test
        fun `should return default when field is missing and default provided`() {
            // Arrange
            val input = mapOf<String, Any?>()

            // Act
            val result: String = mapper.extractField(input, "status", "ACTIVE")

            // Assert
            assertThat(result).isEqualTo("ACTIVE")
        }

        @Test
        fun `should return default when field is null and default provided`() {
            // Arrange
            val input = mapOf("status" to null)

            // Act
            val result: String = mapper.extractField(input, "status", "ACTIVE")

            // Assert
            assertThat(result).isEqualTo("ACTIVE")
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
            val input = mapOf("status" to 12345)

            // Act
            val result: String = mapper.extractField(input, "status", "ACTIVE")

            // Assert - should use default when cast fails (returns null)
            assertThat(result).isEqualTo("ACTIVE")
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
                    "name" to "Test Customer",
                    "email" to "test@example.com",
                    "status" to "INACTIVE",
                )

            // Act
            val result = mapper.mapToInputDTO(input)

            // Assert
            assertThat(result.name).isEqualTo("Test Customer")
            assertThat(result.email).isEqualTo("test@example.com")
            assertThat(result.status).isEqualTo("INACTIVE")
        }

        @Test
        fun `should use default value for optional status field`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Customer",
                    "email" to "test@example.com",
                    // status missing, should use default
                )

            // Act
            val result = mapper.mapToInputDTO(input)

            // Assert
            assertThat(result.name).isEqualTo("Test Customer")
            assertThat(result.email).isEqualTo("test@example.com")
            assertThat(result.status).isEqualTo("ACTIVE") // Default
        }

        @Test
        fun `should throw when required field name is missing`() {
            // Arrange
            val input =
                mapOf(
                    "email" to "test@example.com",
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
        fun `should throw when required field email is missing`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Customer",
                    // email is missing
                )

            // Act & Assert
            assertThatThrownBy {
                mapper.mapToInputDTO(input)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Field 'email' is required")
        }

        @Test
        fun `should use default when optional status field is null`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Customer",
                    "email" to "test@example.com",
                    "status" to null,
                )

            // Act
            val result = mapper.mapToInputDTO(input)

            // Assert
            assertThat(result.status).isEqualTo("ACTIVE") // Default used
        }
    }

    @Nested
    @DisplayName("mapToEntity()")
    inner class MapToEntityTests {
        @Test
        fun `should map DTO to entity for create (no id)`() {
            // Arrange
            val dto =
                CustomerInputDTO(
                    name = "Test Customer",
                    email = "test@example.com",
                    status = "ACTIVE",
                )

            // Act
            val result = mapper.mapToEntity(dto)

            // Assert
            assertThat(result.id).isNull()
            assertThat(result.name).isEqualTo("Test Customer")
            assertThat(result.email).isEqualTo("test@example.com")
            assertThat(result.status).isEqualTo(CustomerStatus.ACTIVE)
            assertThat(result.version).isEqualTo(0L) // Default for new entities
        }

        @Test
        fun `should map DTO to entity for update with existing entity version`() {
            // Arrange
            val id = UUID.randomUUID()
            val existingCustomer =
                Customer(
                    id = id,
                    name = "Existing Customer",
                    email = "existing@example.com",
                    status = CustomerStatus.ACTIVE,
                    version = 5L,
                )

            // Create mapper with function that returns existing customer
            val updateMapper = CustomerGraphQLMapper { existingCustomer }

            val dto =
                CustomerInputDTO(
                    name = "Updated Customer",
                    email = "updated@example.com",
                    status = "INACTIVE",
                )

            // Act
            val result = updateMapper.mapToEntity(dto, id)

            // Assert
            assertThat(result.id).isEqualTo(id)
            assertThat(result.name).isEqualTo("Updated Customer")
            assertThat(result.email).isEqualTo("updated@example.com")
            assertThat(result.status).isEqualTo(CustomerStatus.INACTIVE)
            assertThat(result.version).isEqualTo(5L) // Preserved from existing entity
        }

        @Test
        fun `should map DTO to entity for update when entity not found (uses default version)`() {
            // Arrange
            val id = UUID.randomUUID()
            // Mapper with function that returns null (entity not found)
            val updateMapper = CustomerGraphQLMapper { null }

            val dto =
                CustomerInputDTO(
                    name = "New Customer",
                    email = "new@example.com",
                    status = "PENDING",
                )

            // Act
            val result = updateMapper.mapToEntity(dto, id)

            // Assert
            assertThat(result.id).isEqualTo(id)
            assertThat(result.name).isEqualTo("New Customer")
            assertThat(result.email).isEqualTo("new@example.com")
            assertThat(result.status).isEqualTo(CustomerStatus.PENDING)
            assertThat(result.version).isEqualTo(0L) // Default when entity not found
        }

        @Test
        fun `should throw when status is invalid`() {
            // Arrange
            val dto =
                CustomerInputDTO(
                    name = "Test Customer",
                    email = "test@example.com",
                    status = "INVALID_STATUS",
                )

            // Act & Assert
            assertThatThrownBy {
                mapper.mapToEntity(dto)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid status")
                .hasMessageContaining("INVALID_STATUS")
        }

        @Test
        fun `should handle all valid status values`() {
            // Arrange
            val statuses = listOf("ACTIVE", "INACTIVE", "PENDING")

            statuses.forEach { status ->
                val dto =
                    CustomerInputDTO(
                        name = "Test Customer",
                        email = "test@example.com",
                        status = status,
                    )

                // Act
                val result = mapper.mapToEntity(dto)

                // Assert
                assertThat(result.status).isEqualTo(CustomerStatus.valueOf(status))
            }
        }
    }
}


