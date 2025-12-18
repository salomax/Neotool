package io.github.salomax.neotool.example.test.unit.api

import io.github.salomax.neotool.example.api.ProductController
import io.github.salomax.neotool.example.dto.ProductResponse
import io.github.salomax.neotool.example.service.ProductService
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

@DisplayName("ProductController Unit Tests")
class ProductControllerTest {
    private lateinit var service: ProductService
    private lateinit var controller: ProductController

    @BeforeEach
    fun setUp() {
        service = mock()
        controller = ProductController(service)
    }

    @Nested
    @DisplayName("get()")
    inner class GetTests {
        @Test
        fun `should return empty Optional when product not found`() {
            // Arrange
            val productId = UUID.randomUUID()
            whenever(service.get(productId)).thenReturn(null)

            // Act
            val result = controller.get(productId)

            // Assert
            assertThat(result).isEqualTo(Optional.empty<ProductResponse>())
        }
    }

    @Nested
    @DisplayName("update()")
    inner class UpdateTests {
        @Test
        fun `should return empty Optional when product not found`() {
            // Arrange
            val productId = UUID.randomUUID()
            val request =
                io.github.salomax.neotool.example.dto.UpdateProductRequest(
                    name = "Updated Product",
                    sku = "UPDATED-001",
                    priceCents = 19999L,
                    stock = 20,
                    version = 1L,
                )
            whenever(service.update(any())).thenReturn(null)

            // Act
            val result = controller.update(productId, request)

            // Assert
            assertThat(result).isEqualTo(Optional.empty<ProductResponse>())
        }
    }
}
