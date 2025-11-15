package io.github.salomax.neotool.example.test.integration.api

import io.github.salomax.neotool.example.dto.CustomerResponse
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
@DisplayName("Customer API Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CustomerApiIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    @Test
    @Order(1)
    fun `should list all customers`() {
        val request = HttpRequest.GET<Any>("/api/customers")
        httpClient.exchangeAsString(request)
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()
    }

    @Test
    @Order(2)
    fun `should create a new customer`() {
        val customerInput = TestDataBuilders.customerInput(
            name = "Integration Test Customer",
            email = "integration.test@example.com",
            status = "ACTIVE"
        )

        val request = HttpRequest.POST("/api/customers", customerInput)
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        val body = response.body.get()
        assertThat(body).contains("Integration Test Customer")
        assertThat(body).contains("integration.test@example.com")
    }

    @Test
    @Order(3)
    fun `should get customer by id`() {
        // First create a customer to get
        val customerInput = TestDataBuilders.customerInput(
            name = "Test Customer for Get",
            email = "get.test@example.com",
            status = "ACTIVE"
        )

        val createRequest = HttpRequest.POST("/api/customers", customerInput)
        val createResponse = httpClient.exchangeAsString(createRequest)
        createResponse
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Then get the customer by ID
        val customer = json.read<CustomerResponse>(createResponse)
        val request = HttpRequest.GET<Any>("/api/customers/${customer.id}")
        httpClient.exchangeAsString(request)
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()
    }

    @Test
    @Order(4)
    fun `should handle invalid customer creation`() {
        val invalidInput = mapOf(
            "name" to "", // Empty name should be invalid
            "email" to "invalid-email", // Invalid email format
            "status" to "INVALID_STATUS" // Invalid status
        )

        val request = HttpRequest.POST("/api/customers", invalidInput)
        val exception = assertThrows<HttpClientResponseException> {
          httpClient.exchangeAsString(request)
        }

        // Should return 400 for validation errors
        assert(exception.status == HttpStatus.BAD_REQUEST)
    }

    @Test
    @Order(5)
    fun `should update customer`() {
        // First create a customer
        val customerInput = TestDataBuilders.customerInput(
            name = "Original Customer",
            email = "original@example.com",
            status = "ACTIVE"
        )

        val createRequest = HttpRequest.POST("/api/customers", customerInput)
        val createResponse = httpClient.exchangeAsString(createRequest)
        createResponse
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Then update it
        val customer = json.read<CustomerResponse>(createResponse)
        val updateInput = mapOf(
            "name" to "Updated Customer",
            "email" to "updated@example.com",
            "status" to "INACTIVE",
            "version" to customer.version
        )

        val updateRequest = HttpRequest.PUT("/api/customers/${customer.id}", updateInput)
        httpClient.exchangeAsString(updateRequest)
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()
    }

    @Test
    @Order(6)
    fun `should delete customer`() {
        // First create a customer
        val customerInput = TestDataBuilders.customerInput(
            name = "Customer to Delete",
            email = "delete@example.com",
            status = "ACTIVE"
        )

        val createRequest = HttpRequest.POST("/api/customers", customerInput)
        val createResponse = httpClient.exchangeAsString(createRequest)
        createResponse
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Then delete it
        val customer = json.read<CustomerResponse>(createResponse)
        val deleteRequest = HttpRequest.DELETE<Any>("/api/customers/${customer.id}")
        httpClient.exchangeAsString(deleteRequest)
          .shouldBeSuccessful()
    }

    @Test
    @Order(7)
    fun `should handle non-existent customer operations`() {
        // Try to get non-existent customer
        val getRequest = HttpRequest.GET<Any>("/api/customers/${UUID.randomUUID()}")
        val exceptionGetRequest = assertThrows<HttpClientResponseException> {
          httpClient.exchangeAsString(getRequest)
        }
        assert(exceptionGetRequest.status == HttpStatus.NOT_FOUND)

        // Try to update non-existent customer
        val updateInput = mapOf(
            "name" to "Non-existent Customer",
            "email" to "non.existent@example.com",
            "status" to "ACTIVE",
            "version" to 0L
        )
        val updateRequest = HttpRequest.PUT("/api/customers/${UUID.randomUUID()}", updateInput)
        val exceptionUpdateRequest = assertThrows<HttpClientResponseException> {
          httpClient.exchangeAsString(updateRequest)
        }
        assert(exceptionUpdateRequest.status == HttpStatus.NOT_FOUND)

        // Try to delete non-existent customer
        val deleteRequest = HttpRequest.DELETE<Any>("/api/customers/${UUID.randomUUID()}")
        val exceptionDeleteRequest = assertThrows<HttpClientResponseException> {
          httpClient.exchangeAsString(deleteRequest)
        }
        assert(exceptionDeleteRequest.status == HttpStatus.NOT_FOUND)
    }

    @Test
    @Order(8)
    fun `should handle duplicate email validation`() {
        val customerInput1 = TestDataBuilders.customerInput(
            name = "First Customer",
            email = "duplicate@example.com",
            status = "ACTIVE"
        )

        val customerInput2 = TestDataBuilders.customerInput(
            name = "Second Customer",
            email = "duplicate@example.com", // Same email
            status = "ACTIVE"
        )

        // Create first customer
        val createRequest1 = HttpRequest.POST("/api/customers", customerInput1)
        httpClient.exchangeAsString(createRequest1)
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Try to create second customer with same email
        val createRequest2 = HttpRequest.POST("/api/customers", customerInput2)
        val exceptionCreateRequest2 = assertThrows<HttpClientResponseException> {
          httpClient.exchangeAsString(createRequest2)
        }
        assert(exceptionCreateRequest2.status == HttpStatus.CONFLICT)
    }
}
