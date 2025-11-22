package io.github.salomax.neotool.example.test.unit.graphql

import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.graphql.resolvers.ProductResolver
import io.github.salomax.neotool.example.service.ProductService
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

@DisplayName("ProductResolver Unit Tests")
class ProductResolverTest {
    private lateinit var productService: ProductService
    private lateinit var validator: Validator
    private lateinit var resolver: ProductResolver

    @BeforeEach
    fun setUp() {
        productService = mock()
        validator = mock()
        resolver = ProductResolver(productService, validator)
    }

    @Nested
    @DisplayName("mapToInputDTO() - tested through create()")
    inner class MapToInputDTOTests {
        @Test
        fun `should map input to DTO with all fields`() {
            // Arrange
            val newProduct =
                Product(
                    id = UUID.randomUUID(),
                    name = "Test Product",
                    sku = "TEST-001",
                    priceCents = 9999L,
                    stock = 10,
                )
            val input =
                mapOf(
                    "name" to "Test Product",
                    "sku" to "TEST-001",
                    "priceCents" to 9999L,
                    "stock" to 10,
                )

            // Mock the service
            whenever(productService.create(any())).thenReturn(newProduct)

            // Act - create() calls mapToInputDTO internally
            val result = resolver.create(input)

            // Assert
            assertThat(result.success).isTrue
            assertThat(result.data).isNotNull
            assertThat(result.data?.name).isEqualTo("Test Product")
            assertThat(result.data?.sku).isEqualTo("TEST-001")
            assertThat(result.data?.priceCents).isEqualTo(9999L)
            assertThat(result.data?.stock).isEqualTo(10)
        }

        @Test
        fun `should use default values when optional fields are missing`() {
            // Arrange
            val newProduct =
                Product(
                    id = UUID.randomUUID(),
                    name = "Test Product",
                    sku = "TEST-001",
                    priceCents = 0L,
                    stock = 0,
                )
            val input =
                mapOf(
                    "name" to "Test Product",
                    "sku" to "TEST-001",
                    // priceCents and stock are missing, should use defaults
                )

            // Mock the service
            whenever(productService.create(any())).thenReturn(newProduct)

            // Act - create() calls mapToInputDTO internally
            val result = resolver.create(input)

            // Assert
            assertThat(result.success).isTrue
            assertThat(result.data).isNotNull
            assertThat(result.data?.name).isEqualTo("Test Product")
            assertThat(result.data?.sku).isEqualTo("TEST-001")
            assertThat(result.data?.priceCents).isEqualTo(0L) // Default value
            assertThat(result.data?.stock).isEqualTo(0) // Default value
        }
    }

    @Nested
    @DisplayName("extractField() - via create()")
    inner class ExtractFieldTests {
        @Test
        fun `should return value when field exists and type matches`() {
            // Arrange
            val newProduct =
                Product(
                    id = UUID.randomUUID(),
                    name = "Test Product",
                    sku = "TEST-001",
                    priceCents = 9999L,
                    stock = 10,
                )
            val input =
                mapOf(
                    "name" to "Test Product",
                    "sku" to "TEST-001",
                    "priceCents" to 9999L,
                    "stock" to 10,
                )

            // Mock the service
            whenever(productService.create(any())).thenReturn(newProduct)

            // Act - create() calls mapToInputDTO which calls extractField
            val result = resolver.create(input)

            // Assert
            assertThat(result.success).isTrue
            assertThat(result.data?.name).isEqualTo("Test Product")
            assertThat(result.data?.sku).isEqualTo("TEST-001")
            assertThat(result.data?.priceCents).isEqualTo(9999L)
            assertThat(result.data?.stock).isEqualTo(10)
        }

        @Test
        fun `should return default when field missing and default provided`() {
            // Arrange
            val newProduct =
                Product(
                    id = UUID.randomUUID(),
                    name = "Test Product",
                    sku = "TEST-001",
                    priceCents = 0L,
                    stock = 0,
                )
            val input =
                mapOf(
                    "name" to "Test Product",
                    "sku" to "TEST-001",
                    // priceCents and stock missing, should use defaults
                )

            // Mock the service
            whenever(productService.create(any())).thenReturn(newProduct)

            // Act - create() calls mapToInputDTO which calls extractField
            val result = resolver.create(input)

            // Assert
            assertThat(result.success).isTrue
            assertThat(result.data?.priceCents).isEqualTo(0L) // Default
            assertThat(result.data?.stock).isEqualTo(0) // Default
        }

        @Test
        fun `should throw exception when required field is missing and no default`() {
            // Arrange
            val input =
                mapOf(
                    "sku" to "TEST-001",
                    // name is missing and has no default
                )

            // Act & Assert - create() calls mapToInputDTO which calls extractField
            val result = resolver.create(input)
            assertThat(result.success).isFalse
            assertThat(result.errors).isNotEmpty
            assertThat(result.errors[0].message).contains("Field 'name' is required")
        }

        @Test
        fun `should throw exception when sku field is missing and no default`() {
            // Arrange
            val input =
                mapOf(
                    "name" to "Test Product",
                    // sku is missing and has no default
                )

            // Act & Assert - create() calls mapToInputDTO which calls extractField
            val result = resolver.create(input)
            assertThat(result.success).isFalse
            assertThat(result.errors).isNotEmpty
            assertThat(result.errors[0].message).contains("Field 'sku' is required")
        }
    }
}
