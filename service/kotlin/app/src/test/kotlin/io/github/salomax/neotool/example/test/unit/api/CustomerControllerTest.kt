package io.github.salomax.neotool.example.test.unit.api

import io.github.salomax.neotool.example.api.CustomerController
import io.github.salomax.neotool.example.dto.CustomerResponse
import io.github.salomax.neotool.example.service.CustomerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("CustomerController Unit Tests")
class CustomerControllerTest {

    private lateinit var service: CustomerService
    private lateinit var controller: CustomerController

    @BeforeEach
    fun setUp() {
        service = mock()
        controller = CustomerController(service)
    }

    @Nested
    @DisplayName("get()")
    inner class GetTests {

        @Test
        fun `should return empty Optional when customer not found`() {
            // Arrange
            val customerId = UUID.randomUUID()
            whenever(service.get(customerId)).thenReturn(null)

            // Act
            val result = controller.get(customerId)

            // Assert
            assertThat(result).isEqualTo(Optional.empty<CustomerResponse>())
        }
    }

    @Nested
    @DisplayName("update()")
    inner class UpdateTests {

        @Test
        fun `should return empty Optional when customer not found`() {
            // Arrange
            val customerId = UUID.randomUUID()
            val request = io.github.salomax.neotool.example.dto.UpdateCustomerRequest(
                name = "Updated Customer",
                email = "updated@example.com",
                status = "ACTIVE",
                version = 1L
            )
            whenever(service.update(any())).thenReturn(null)

            // Act
            val result = controller.update(customerId, request)

            // Assert
            assertThat(result).isEqualTo(Optional.empty<CustomerResponse>())
        }
    }
}

