package io.github.salomax.neotool.example.test.integration.api

import io.github.salomax.neotool.example.dto.ProductResponse
import io.github.salomax.neotool.example.test.TestDataBuilders
import io.github.salomax.neotool.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.test.assertions.shouldBeJson
import io.github.salomax.neotool.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.test.http.exchangeAsString
import io.github.salomax.neotool.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.test.json.read
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.assertThrows
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Product API Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ProductApiIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    @Test
    @Order(1)
    fun `should list all products`() {
        val request = HttpRequest.GET<Any>("/api/products")
        httpClient.exchangeAsString(request)
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()
    }

    @Test
    @Order(2)
    fun `should create a new product`() {
        val productInput = TestDataBuilders.productInput(
            name = "Integration Test Product",
            sku = "INT-TEST-001",
            priceCents = 9999L,
            stock = 10
        )

        val request = HttpRequest.POST("/api/products", productInput)
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        val body = response.body.get()
        assertThat(body).contains("Integration Test Product")
        assertThat(body).contains("INT-TEST-001")
    }

    @Test
    @Order(3)
    fun `should get product by id`() {
        // First create a product to get
        val productInput = TestDataBuilders.productInput(
            name = "Test Product for Get",
            sku = "GET-TEST-001",
            priceCents = 19999L,
            stock = 5
        )

        val createRequest = HttpRequest.POST("/api/products", productInput)
        val createResponse = httpClient.exchangeAsString(createRequest)
        createResponse
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Then get the product by ID
        val product = json.read<ProductResponse>(createResponse)
        val request = HttpRequest.GET<Any>("/api/products/${product.id}")
        httpClient.exchangeAsString(request)
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()
    }

    @Test
    @Order(4)
    fun `should handle invalid product creation`() {
        val invalidInput = mapOf(
            "name" to "", // Empty name should be invalid
            "sku" to "INVALID",
            "priceCents" to -100L, // Negative price should be invalid
            "stock" to -5 // Negative stock should be invalid
        )

        val request = HttpRequest.POST("/api/products", invalidInput)
        val response = httpClient.exchangeAsString(request)

        response
            .shouldBeSuccessful()
            .shouldBeJson()
            .shouldHaveNonEmptyBody()
    }

    @Test
    @Order(5)
    fun `should update product`() {
        // First create a product
        val productInput = TestDataBuilders.productInput(
            name = "Original Product",
            sku = "UPDATE-TEST-001",
            priceCents = 10000L,
            stock = 10
        )

        val createRequest = HttpRequest.POST("/api/products", productInput)
        val createResponse = httpClient.exchangeAsString(createRequest)
        createResponse
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Then update it
        val updateInput = mapOf(
            "name" to "Updated Product",
            "sku" to "UPDATE-TEST-001",
            "priceCents" to 15000L,
            "stock" to 15
        )

        val product = json.read<ProductResponse>(createResponse)
        val updateRequest = HttpRequest.PUT("/api/products/${product.id}", updateInput)
        httpClient.exchangeAsString(updateRequest)
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()
    }

    @Test
    @Order(6)
    fun `should delete product`() {
        // First create a product
        val productInput = TestDataBuilders.productInput(
            name = "Product to Delete",
            sku = "DELETE-TEST-001",
            priceCents = 5000L,
            stock = 3
        )

        val createRequest = HttpRequest.POST("/api/products", productInput)
        val createResponse = httpClient.exchangeAsString(createRequest)
        createResponse
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Then delete it
        val product = json.read<ProductResponse>(createResponse)
        val deleteRequest = HttpRequest.DELETE<Any>("/api/products/${product.id}")
        httpClient.exchangeAsString(deleteRequest)
          .shouldBeSuccessful()
    }

    @Test
    @Order(7)
    fun `should handle non-existent product operations`() {
        // Try to get non-existent product
        val getRequest = HttpRequest.GET<Any>("/api/products/${UUID.randomUUID()}")
        val exceptionGetRequest = assertThrows<HttpClientResponseException> {
            httpClient.exchangeAsString(getRequest)
        }
        assert(exceptionGetRequest.status == HttpStatus.NOT_FOUND)

        // Try to update non-existent product
        val updateInput = TestDataBuilders.productInput(
            name = "Non-existent Product",
            sku = "NON-EXISTENT-001",
            priceCents = 1000L,
            stock = 1
        )
        val updateRequest = HttpRequest.PUT("/api/products/${UUID.randomUUID()}", updateInput)
        val exceptionUpdateRequest = assertThrows<HttpClientResponseException> {
            httpClient.exchangeAsString(updateRequest)
        }
        assert(exceptionUpdateRequest.status == HttpStatus.CONFLICT)

        // Try to delete non-existent product
        val deleteRequest = HttpRequest.DELETE<Any>("/api/products/${UUID.randomUUID()}")
        val exceptionDeleteRequest = assertThrows<HttpClientResponseException> {
            httpClient.exchangeAsString(deleteRequest)
        }
        assert(exceptionDeleteRequest.status == HttpStatus.NOT_FOUND)
    }

    @Test
    @Order(8)
    fun `should handle duplicate sku validation`() {
        val productInput1 = TestDataBuilders.productInput(
            name = "First Product",
            sku = "DUPLICATE-SKU-001",
            priceCents = 1000L,
            stock = 5
        )

        val productInput2 = TestDataBuilders.productInput(
            name = "Second Product",
            sku = "DUPLICATE-SKU-001", // Same SKU
            priceCents = 2000L,
            stock = 3
        )

        // Create first product
        val createRequest1 = HttpRequest.POST("/api/products", productInput1)
        httpClient.exchangeAsString(createRequest1)
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Try to create second product with same SKU
        val createRequest2 = HttpRequest.POST("/api/products", productInput2)
        val exceptionCreateRequest2 = assertThrows<HttpClientResponseException> {
            httpClient.exchangeAsString(createRequest2)
        }
        assert(exceptionCreateRequest2.status == HttpStatus.CONFLICT)
    }
}
