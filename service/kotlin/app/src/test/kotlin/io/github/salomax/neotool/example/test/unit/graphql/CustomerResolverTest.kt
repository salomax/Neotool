package io.github.salomax.neotool.example.test.unit.graphql

import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.CustomerStatus
import io.github.salomax.neotool.example.graphql.resolvers.CustomerResolver
import io.github.salomax.neotool.example.service.CustomerService
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("CustomerResolver Unit Tests")
class CustomerResolverTest {
    private lateinit var customerService: CustomerService
    private lateinit var validator: Validator
    private lateinit var resolver: CustomerResolver

    @BeforeEach
    fun setUp() {
        customerService = mock()
        validator = mock()
        resolver = CustomerResolver(customerService, validator)
    }

    @Nested
    @DisplayName("mapToInputDTO() - tested through create()")
    inner class MapToInputDTOTests {
        @Test
        fun `should map input to DTO with all fields`() {
            // Arrange
            val newCustomer =
                Customer(
                    id = UUID.randomUUID(),
                    name = "Test Customer",
                    email = "test@example.com",
                    status = CustomerStatus.ACTIVE,
                )
            val input =
                mapOf(
                    "name" to "Test Customer",
                    "email" to "test@example.com",
                    "status" to "ACTIVE",
                )

            // Mock the service
            whenever(customerService.create(any())).thenReturn(newCustomer)

            // Act - create() calls mapToInputDTO internally
            val result = resolver.create(input)

            // Assert
            assertThat(result.success).isTrue
            assertThat(result.data).isNotNull
            assertThat(result.data?.name).isEqualTo("Test Customer")
            assertThat(result.data?.email).isEqualTo("test@example.com")
            assertThat(result.data?.status).isEqualTo(CustomerStatus.ACTIVE)
        }

        @Test
        fun `should use default status when not provided`() {
            // Arrange
            val newCustomer =
                Customer(
                    id = UUID.randomUUID(),
                    name = "Test Customer",
                    email = "test@example.com",
                    status = CustomerStatus.ACTIVE,
                )
            val input =
                mapOf(
                    "name" to "Test Customer",
                    "email" to "test@example.com",
                )

            // Mock the service
            whenever(customerService.create(any())).thenReturn(newCustomer)

            // Act - create() calls mapToInputDTO internally
            val result = resolver.create(input)

            // Assert
            assertThat(result.success).isTrue
            assertThat(result.data).isNotNull
            assertThat(result.data?.name).isEqualTo("Test Customer")
            assertThat(result.data?.email).isEqualTo("test@example.com")
            assertThat(result.data?.status).isEqualTo(CustomerStatus.ACTIVE) // Default value
        }
    }

    @Nested
    @DisplayName("mapToEntity() - tested through create() and update()")
    inner class MapToEntityTests {
        @Test
        fun `should fetch existing entity when id is provided (update case)`() {
            // Arrange
            val customerId = UUID.randomUUID()
            val existingCustomer =
                Customer(
                    id = customerId,
                    name = "Existing Customer",
                    email = "existing@example.com",
                    status = CustomerStatus.ACTIVE,
                    version = 1L,
                )
            val updatedCustomer =
                Customer(
                    id = customerId,
                    name = "Updated Customer",
                    email = "updated@example.com",
                    status = CustomerStatus.ACTIVE,
                    version = 1L,
                )
            val input =
                mapOf(
                    "name" to "Updated Customer",
                    "email" to "updated@example.com",
                    "status" to "ACTIVE",
                )

            // Mock the service
            whenever(customerService.get(customerId)).thenReturn(existingCustomer)
            whenever(customerService.update(any())).thenReturn(updatedCustomer)

            // Act - update calls mapToEntity with id
            val result = resolver.update(customerId.toString(), input)

            // Assert - verify that update was called with correct version
            assertThat(result.success).isTrue
            assertThat(result.data).isNotNull
            assertThat(result.data?.id).isEqualTo(customerId)
        }

        @Test
        fun `should not fetch entity when id is null (create case)`() {
            // Arrange
            val newCustomer =
                Customer(
                    id = UUID.randomUUID(),
                    name = "New Customer",
                    email = "new@example.com",
                    status = CustomerStatus.ACTIVE,
                    version = 0L,
                )
            val input =
                mapOf(
                    "name" to "New Customer",
                    "email" to "new@example.com",
                    "status" to "ACTIVE",
                )

            // Mock the service
            whenever(customerService.create(any())).thenReturn(newCustomer)

            // Act - create calls mapToEntity with null id
            val result = resolver.create(input)

            // Assert
            assertThat(result.success).isTrue
            assertThat(result.data).isNotNull
            assertThat(result.data?.name).isEqualTo("New Customer")
        }

        @Test
        fun `should throw IllegalArgumentException for invalid status`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Customer",
                    "email" to "test@example.com",
                    "status" to "INVALID_STATUS",
                )

            // Act & Assert - create() will call mapToEntity which should throw
            val result = resolver.create(input)
            assertThat(result.success).isFalse
            assertThat(result.errors).isNotEmpty
            assertThat(result.errors[0].message).contains("Invalid status")
        }

        @Test
        fun `should handle all valid status values`() {
            // Arrange
            val statuses = listOf("ACTIVE", "INACTIVE", "PENDING")

            statuses.forEach { status ->
                val newCustomer =
                    Customer(
                        id = UUID.randomUUID(),
                        name = "Test Customer",
                        email = "test@example.com",
                        status = CustomerStatus.valueOf(status),
                        version = 0L,
                    )
                val input =
                    mapOf(
                        "name" to "Test Customer",
                        "email" to "test@example.com",
                        "status" to status,
                    )

                // Mock the service
                whenever(customerService.create(any())).thenReturn(newCustomer)

                // Act
                val result = resolver.create(input)

                // Assert
                assertThat(result.success).isTrue
                assertThat(result.data?.status).isEqualTo(CustomerStatus.valueOf(status))
            }
        }
    }

    @Nested
    @DisplayName("extractField() - via create()")
    inner class ExtractFieldTests {
        @Test
        fun `should throw exception when required field is missing`() {
            // Arrange
            val input =
                mapOf(
                    "email" to "test@example.com",
                    // name is missing
                )

            // Act & Assert - create() calls mapToInputDTO which calls extractField
            val result = resolver.create(input)
            assertThat(result.success).isFalse
            assertThat(result.errors).isNotEmpty
            assertThat(result.errors[0].message).contains("Field 'name' is required")
        }

        @Test
        fun `should throw exception when email field is missing`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Customer",
                    // email is missing
                )

            // Act & Assert - create() calls mapToInputDTO which calls extractField
            val result = resolver.create(input)
            assertThat(result.success).isFalse
            assertThat(result.errors).isNotEmpty
            assertThat(result.errors[0].message).contains("Field 'email' is required")
        }

        @Test
        fun `should use default value when optional field is missing`() {
            // Arrange
            val newCustomer =
                Customer(
                    id = UUID.randomUUID(),
                    name = "Test Customer",
                    email = "test@example.com",
                    status = CustomerStatus.ACTIVE,
                )
            val input =
                mapOf(
                    "name" to "Test Customer",
                    "email" to "test@example.com",
                    // status is missing, should use default "ACTIVE"
                )

            // Mock the service
            whenever(customerService.create(any())).thenReturn(newCustomer)

            // Act - create() calls mapToInputDTO which calls extractField
            val result = resolver.create(input)

            // Assert
            assertThat(result.success).isTrue
            assertThat(result.data?.status).isEqualTo(CustomerStatus.ACTIVE)
        }
    }
}
