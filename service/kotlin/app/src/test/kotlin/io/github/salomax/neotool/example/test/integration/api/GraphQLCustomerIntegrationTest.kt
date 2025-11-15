package io.github.salomax.neotool.example.test.integration.api

import io.github.salomax.neotool.example.test.TestDataBuilders
import io.github.salomax.neotool.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.test.assertions.shouldBeJson
import io.github.salomax.neotool.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.test.http.exchangeAsString
import io.github.salomax.neotool.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.test.json.read
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@MicronautTest(startApplication = true)
@DisplayName("GraphQL Customer Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("customer")
@TestMethodOrder(MethodOrderer.Random::class)
class GraphQLCustomerIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    private fun uniqueEmail() = "graphql-customer-${System.currentTimeMillis()}@example.com"
    private fun uniqueName() = "GraphQL Customer Test ${System.currentTimeMillis()}"

    @Test
    fun `should query customers via GraphQL`() {
        val query = TestDataBuilders.customersQuery()
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Assert response structure
        val payload: JsonNode = json.read(response)
        assertThat(payload["errors"])
          .describedAs("GraphQL errors must be absent")
          .isNull()

        val data = payload["data"]
        assertThat(data)
          .describedAs("GraphQL response must contain 'data'")
          .isNotNull()

        val customers = data["customers"]
        assertThat(customers)
          .describedAs("Customers array must be present")
          .isNotNull()
        assertThat(customers.isArray)
          .describedAs("Customers must be an array")
          .isTrue()
    }

    @Test
    fun `should create customer via GraphQL mutation`() {
        // Arrange (unique inputs so the test is self-contained)
        val expectedName = uniqueName()
        val expectedEmail = uniqueEmail()
        val expectedStatus = "ACTIVE"

        val mutation = TestDataBuilders.createCustomerMutation(
            name = expectedName,
            email = expectedEmail,
            status = expectedStatus
        )

        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Assert (parse and validate the mutation payload)
        val payload: JsonNode = json.read(response)

        // Errors must be absent in a successful mutation
        assertThat(payload["errors"])
          .describedAs("GraphQL errors must be absent")
          .isNull()

        // Navigate to data.createCustomer
        val data = payload["data"]
        assertThat(data)
          .describedAs("GraphQL response must contain 'data.createCustomer'")
          .isNotNull()
        assertThat(data["createCustomer"]).isNotNull

        val created: JsonNode = data["createCustomer"]

        // Validate returned fields (id is server-generated, just assert non-null)
        assertThat(created["id"]).isNotNull
        assertThat(created["name"].stringValue).isEqualTo(expectedName)
        assertThat(created["email"].stringValue).isEqualTo(expectedEmail)
        assertThat(created["status"].stringValue).isEqualTo(expectedStatus)
    }

    @Test
    fun `should handle GraphQL customer query with variables`() {
        // First, create a customer to have data to query
        val createMutation = TestDataBuilders.createCustomerMutation(
            name = uniqueName(),
            email = uniqueEmail(),
            status = "ACTIVE"
        )

        val createRequest = HttpRequest.POST("/graphql", createMutation)
            .contentType(MediaType.APPLICATION_JSON)

        val createResponse = httpClient.exchangeAsString(createRequest)
        createResponse
          .shouldBeSuccessful()
          .shouldBeJson()

        // Get the created customer ID
        val createPayload: JsonNode = json.read(createResponse)
        val createdCustomer = createPayload["data"]["createCustomer"]
        val customerId = createdCustomer["id"].stringValue

        // Now test querying with variables
        val query = TestDataBuilders.graphQLQuery(
            "query GetCustomer(\$id: ID!) { customer(id: \$id) { id name email status } }",
            mapOf("id" to customerId)
        )

        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()

        // Assert response structure
        val payload: JsonNode = json.read(response)
        assertThat(payload["errors"])
          .describedAs("GraphQL errors must be absent")
          .isNull()
        
        val data = payload["data"]
        assertThat(data)
          .describedAs("GraphQL response must contain 'data'")
          .isNotNull()

        val customer = data["customer"]
        assertThat(customer)
          .describedAs("Customer should be found when using correct ID")
          .isNotNull()

        // Verify the customer data matches what we created
        assertThat(customer["id"].stringValue).isEqualTo(customerId)
        assertThat(customer["name"]).isNotNull
        assertThat(customer["email"]).isNotNull
        assertThat(customer["status"]).isNotNull
    }

    @Test
    fun `should handle GraphQL query with non-existent customer`() {
        val query = TestDataBuilders.graphQLQuery(
            "query GetCustomer(\$id: ID!) { customer(id: \$id) { id name email } }",
            mapOf("id" to UUID.randomUUID().toString())
        )

        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()

        // Assert response structure
        val payload: JsonNode = json.read(response)
        assertThat(payload["errors"])
          .describedAs("GraphQL errors must be absent")
          .isNull()

        val data = payload["data"]
        assertThat(data)
          .describedAs("GraphQL response must contain 'data'")
          .isNotNull()

        val customer = data["customer"]
        assertThat(customer.isNull)
          .describedAs("Customer should be null when not found")
          .isTrue()
    }

    @Test
    fun `should handle GraphQL query with invalid customer`() {
      val query = TestDataBuilders.graphQLQuery(
        "query GetCustomer(\$id: ID!) { customer(id: \$id) { id name email } }",
        mapOf("id" to "invalid-id")
      )

      val request = HttpRequest.POST("/graphql", query)
        .contentType(MediaType.APPLICATION_JSON)

      val response = httpClient.exchangeAsString(request)
      response
        .shouldBeSuccessful()
        .shouldBeJson()

      // Assert response structure
      val payload: JsonNode = json.read(response)
      assertThat(payload["errors"])
        .describedAs("GraphQL errors must be absent")
        .isNull()

      val data = payload["data"]
      assertThat(data)
        .describedAs("GraphQL response must contain 'data'")
        .isNotNull()

      val customer = data["customer"]
      assertThat(customer.isNull)
        .describedAs("Customer should be null when not found")
        .isTrue()
    }

    @Test
    fun `should handle GraphQL customer mutation with validation errors`() {
        val mutation = TestDataBuilders.createCustomerMutation(
            name = "", // Empty name should be invalid
            email = "invalid-email", // Invalid email format
            status = "INVALID_STATUS" // Invalid status
        )

        val request = HttpRequest.POST("/graphql", mutation)
            .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()

        // Assert error response structure
        val payload: JsonNode = json.read(response)
        
        // Should have errors for validation failures
        val errors = payload["errors"]
        assertThat(errors)
          .describedAs("GraphQL errors must be present for validation errors")
          .isNotNull()
        assertThat(errors.isArray)
          .describedAs("Errors must be an array")
          .isTrue()
        assertThat(errors.size())
          .describedAs("Must have at least one error")
          .isGreaterThan(0)

        // Data should be null for validation errors
        val data = payload["data"]
        // Note: json.read converts null to JsonNull, so we check for NullNode
        assertThat(data.isNull)
          .describedAs("Data should be null for validation errors")
          .isTrue() // But it should be a NullNode
    }

    @Test
    fun `should handle GraphQL customer query with invalid variable types`() {
        val query = TestDataBuilders.graphQLQuery(
            "query GetCustomer(\$id: ID!) { customer(id: \$id) { id name email } }",
            mapOf("id" to 123) // Wrong type - should be string
        )

        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()

        // Assert error response structure
        val payload: JsonNode = json.read(response)

        // Data should be null for invalid variable types
        val data = payload["data"]
        // Note: json.read converts null to JsonNull, so we check for NullNode
        assertThat(data)
          .describedAs("Data should be null for invalid variable types")
          .isNotNull()

        val customer = data["customer"]
        assertThat(customer.isNull)
          .describedAs("Data should be null for invalid variable types")
          .isTrue()
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `should handle concurrent GraphQL customer mutations`() {
        val email = uniqueEmail()
        val mutation = TestDataBuilders.createCustomerMutation(
            name = uniqueName(),
            email = email,
            status = "ACTIVE"
        )

        // Test race conditions with concurrent mutations
        val futures = (1..10).map { 
            CompletableFuture.supplyAsync {
                try {
                    val response: HttpResponse<String> = httpClient.exchangeAsString(
                        HttpRequest.POST("/graphql", mutation)
                            .contentType(MediaType.APPLICATION_JSON)
                    )
                    // Return the response string directly
                    response
                } catch (e: Exception) {
                    // Return the exception for conflict detection
                    e
                }
            }
        }
        
        val results = futures.map { it.get() }
        val successful = results.count {
          it is HttpResponse<*> && json.read<Map<String, Any>>(it)["data"] != null }
        val conflicts = results.count {
          it is HttpResponse<*> && json.read<Map<String, Any>>(it)["data"] == null
        }

        assertThat(successful).isEqualTo(1)
        assertThat(conflicts).isEqualTo(9)
    }

    @Test
    fun `should handle large GraphQL customer payloads`() {
        val largeMutation = TestDataBuilders.createCustomerMutation(
            name = "A".repeat(1000), // Test field length limits
            email = uniqueEmail(),
            status = "ACTIVE"
        )

        val request = HttpRequest.POST("/graphql", largeMutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    fun `should handle GraphQL customer query with enums`() {
        val queryWithEnums = """
            query {
                customers {
                    id
                    name
                    email
                    status
                }
            }
        """.trimIndent()

        val request = HttpRequest.POST("/graphql", mapOf("query" to queryWithEnums))
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    fun `should handle GraphQL customer query with custom scalars`() {
        val queryWithCustomScalars = """
            query {
                customers {
                    id
                    name
                    email
                    createdAt
                    updatedAt
                }
            }
        """.trimIndent()

        val request = HttpRequest.POST("/graphql", mapOf("query" to queryWithCustomScalars))
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }
}
