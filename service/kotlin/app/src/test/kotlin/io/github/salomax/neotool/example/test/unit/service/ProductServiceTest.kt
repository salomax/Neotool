package io.github.salomax.neotool.example.test.unit.service

import io.github.salomax.neotool.example.entity.ProductEntity
import io.github.salomax.neotool.example.repo.ProductRepository
import io.github.salomax.neotool.example.service.ProductService
import io.micronaut.http.server.exceptions.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("ProductService Unit Tests")
class ProductServiceTest {
    private lateinit var repository: ProductRepository
    private lateinit var service: ProductService

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = ProductService(repository)
    }

    @Nested
    @DisplayName("get()")
    inner class GetTests {
        @Test
        fun `should return product when found`() {
            // Arrange
            val productId = UUID.randomUUID()
            val entity =
                ProductEntity(
                    id = productId,
                    name = "Test Product",
                    sku = "TEST-001",
                    priceCents = 9999L,
                    stock = 10,
                )
            whenever(repository.findById(productId)).thenReturn(Optional.of(entity))

            // Act
            val result = service.get(productId)

            // Assert
            assertThat(result).isNotNull
            assertThat(result?.id).isEqualTo(productId)
            assertThat(result?.name).isEqualTo("Test Product")
            verify(repository).findById(productId)
        }

        @Test
        fun `should return null when product not found`() {
            // Arrange
            val productId = UUID.randomUUID()
            whenever(repository.findById(productId)).thenReturn(Optional.empty())

            // Act
            val result = service.get(productId)

            // Assert
            assertThat(result).isNull()
            verify(repository).findById(productId)
        }
    }

    @Nested
    @DisplayName("delete()")
    inner class DeleteTests {
        @Test
        fun `should delete product when found`() {
            // Arrange
            val productId = UUID.randomUUID()
            val entity =
                ProductEntity(
                    id = productId,
                    name = "Test Product",
                    sku = "TEST-001",
                    priceCents = 9999L,
                    stock = 10,
                )
            whenever(repository.findById(productId)).thenReturn(Optional.of(entity))

            // Act
            service.delete(productId)

            // Assert
            verify(repository).findById(productId)
            verify(repository).delete(entity)
        }

        @Test
        fun `should throw NotFoundException when product not found`() {
            // Arrange
            val productId = UUID.randomUUID()
            whenever(repository.findById(productId)).thenReturn(Optional.empty())

            // Act & Assert
            assertThatThrownBy { service.delete(productId) }
                .isInstanceOf(NotFoundException::class.java)
            verify(repository).findById(productId)
            verify(repository, org.mockito.kotlin.never()).delete(any())
        }
    }
}
