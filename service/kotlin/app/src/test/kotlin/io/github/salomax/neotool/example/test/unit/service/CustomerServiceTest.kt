package io.github.salomax.neotool.example.test.unit.service

import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.CustomerStatus
import io.github.salomax.neotool.example.entity.CustomerEntity
import io.github.salomax.neotool.example.repo.CustomerRepository
import io.github.salomax.neotool.example.service.CustomerService
import io.micronaut.http.server.exceptions.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.StaleObjectStateException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@DisplayName("CustomerService Unit Tests")
class CustomerServiceTest {

    private lateinit var repository: CustomerRepository
    private lateinit var service: CustomerService

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = CustomerService(repository)
    }

    @Nested
    @DisplayName("get()")
    inner class GetTests {

        @Test
        fun `should return customer when found`() {
            // Arrange
            val customerId = UUID.randomUUID()
            val entity = CustomerEntity(
                id = customerId,
                name = "Test Customer",
                email = "test@example.com",
                status = CustomerStatus.ACTIVE,
                version = 0L
            )
            whenever(repository.findById(customerId)).thenReturn(Optional.of(entity))

            // Act
            val result = service.get(customerId)

            // Assert
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(customerId)
            assertThat(result?.name).isEqualTo("Test Customer")
            verify(repository).findById(customerId)
        }

        @Test
        fun `should return null when customer not found`() {
            // Arrange
            val customerId = UUID.randomUUID()
            whenever(repository.findById(customerId)).thenReturn(Optional.empty())

            // Act
            val result = service.get(customerId)

            // Assert
            assertThat(result).isNull()
            verify(repository).findById(customerId)
        }
    }

    @Nested
    @DisplayName("update()")
    inner class UpdateTests {

        @Test
        fun `should throw NotFoundException when customer not found`() {
            // Arrange
            val customerId = UUID.randomUUID()
            val customer = Customer(
                id = customerId,
                name = "Updated Customer",
                email = "updated@example.com",
                status = CustomerStatus.ACTIVE,
                version = 0L
            )
            whenever(repository.findById(customerId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThatThrownBy { service.update(customer) }
                .isInstanceOf(NotFoundException::class.java)
            verify(repository).findById(customerId)
            verify(repository, never()).save(any())
        }

        @Test
        fun `should throw StaleObjectStateException when version mismatch`() {
            // Arrange
            val customerId = UUID.randomUUID()
            val existingEntity = CustomerEntity(
                id = customerId,
                name = "Existing Customer",
                email = "existing@example.com",
                status = CustomerStatus.ACTIVE,
                version = 1L
            )
            val customer = Customer(
                id = customerId,
                name = "Updated Customer",
                email = "updated@example.com",
                status = CustomerStatus.ACTIVE,
                version = 0L // Different version
            )
            whenever(repository.findById(customerId)).thenReturn(Optional.of(existingEntity))

            // Act & Assert
            assertThatThrownBy { service.update(customer) }
                .isInstanceOf(StaleObjectStateException::class.java)
                .hasMessageContaining("was modified by another user")
            verify(repository).findById(customerId)
            verify(repository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("delete()")
    inner class DeleteTests {

        @Test
        fun `should delete customer when found`() {
            // Arrange
            val customerId = UUID.randomUUID()
            val entity = CustomerEntity(
                id = customerId,
                name = "Test Customer",
                email = "test@example.com",
                status = CustomerStatus.ACTIVE,
                version = 0L
            )
            whenever(repository.findById(customerId)).thenReturn(Optional.of(entity))

            // Act
            service.delete(customerId)

            // Assert
            verify(repository).findById(customerId)
            verify(repository).delete(entity)
        }

        @Test
        fun `should throw NotFoundException when customer not found`() {
            // Arrange
            val customerId = UUID.randomUUID()
            whenever(repository.findById(customerId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThatThrownBy { service.delete(customerId) }
                .isInstanceOf(NotFoundException::class.java)
            verify(repository).findById(customerId)
            verify(repository, never()).delete(any())
        }
    }
}

