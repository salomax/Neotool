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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@MicronautTest(startApplication = true)
@DisplayName("GraphQL Product Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("product")
@TestMethodOrder(MethodOrderer.Random::class)
class GraphQLProductIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    private fun uniqueSku() = "GRAPHQL-PRODUCT-${System.currentTimeMillis()}-${Thread.currentThread().id}"
    private fun uniqueName() = "GraphQL Product Test ${System.currentTimeMillis()}"

    @Test
    fun `should query products via GraphQL`() {
        val query = TestDataBuilders.productsQuery()
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

        val products = data["products"]
        assertThat(products)
          .describedAs("Products array must be present")
          .isNotNull()
        assertThat(products.isArray)
          .describedAs("Products must be an array")
          .isTrue()
    }

    @Test
    fun `should create product via GraphQL mutation`() {
      // Arrange (unique inputs so the test is self-contained)
      val expectedName = uniqueName()
      val expectedSku = uniqueSku()
      val expectedPrice = 25_000L
      val expectedStock = 20

      val mutation = TestDataBuilders.createProductMutation(
        name = expectedName,
        sku = expectedSku,
        priceCents = expectedPrice,
        stock = expectedStock
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
      // (GraphQL usually returns 200 with "errors" array when it fails)
      assertThat(payload["errors"])
        .describedAs("GraphQL errors must be absent")
        .isNull()

      // Navigate to data.createProduct
      val data = payload["data"]
      assertThat(data)
        .describedAs("GraphQL response must contain 'data.createProduct'")
      assertThat(data["createProduct"]).isNotNull

      val created: JsonNode = data["createProduct"]

      // Validate returned fields (id is server-generated, just assert non-null)
      assertThat(created["id"]).isNotNull
      assertThat(created["name"].stringValue).isEqualTo(expectedName)
      assertThat(created["name"].stringValue).isEqualTo(expectedName)
      assertThat(created["sku"].stringValue).isEqualTo(expectedSku)
      assertThat((created["priceCents"].numberValue).toLong())
        .isEqualTo(expectedPrice)
      assertThat((created["stock"].numberValue).toInt())
        .isEqualTo(expectedStock)
    }

    @Test
    fun `should handle GraphQL query with variables`() {
        // First, create a product to have data to query
        val createMutation = TestDataBuilders.createProductMutation(
            name = uniqueName(),
            sku = uniqueSku(),
            priceCents = 15000L,
            stock = 25
        )

        val createRequest = HttpRequest.POST("/graphql", createMutation)
            .contentType(MediaType.APPLICATION_JSON)

        val createResponse = httpClient.exchangeAsString(createRequest)
        createResponse
          .shouldBeSuccessful()
          .shouldBeJson()

        // Get the created product ID
        val createPayload: JsonNode = json.read(createResponse)
        val createdProduct = createPayload["data"]["createProduct"]
        val productId = createdProduct["id"].stringValue

        // Now test querying with variables
        val query = TestDataBuilders.graphQLQuery(
            "query GetProduct(\$id: ID!) { product(id: \$id) { id name sku priceCents stock } }",
            mapOf("id" to productId)
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

        val product = data["product"]
        assertThat(product)
          .describedAs("Product should be found when using correct ID")
          .isNotNull()

        // Verify the product data matches what we created
        assertThat(product["id"].stringValue).isEqualTo(productId)
        assertThat(product["name"]).isNotNull
        assertThat(product["sku"]).isNotNull
        assertThat(product["priceCents"]).isNotNull
        assertThat(product["stock"]).isNotNull
    }

    @Test
    fun `should handle GraphQL query with non-existent product`() {
        val query = TestDataBuilders.graphQLQuery(
            "query GetProduct(\$id: ID!) { product(id: \$id) { id name sku } }",
            mapOf("id" to "non-existent-id")
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

        val product = data["product"]
        // Note: json.read converts null to JsonNull, so we check for NullNode
        assertThat(product.isNull)
          .describedAs("Product should be null when not found")
          .isTrue() // JsonNull is not null
    }

    @Test
    fun `should handle GraphQL mutation with validation errors`() {
        val mutation = TestDataBuilders.createProductMutation(
            name = "", // Empty name should be invalid
            sku = "INVALID-SKU",
            priceCents = -100L, // Negative price should be invalid
            stock = -5 // Negative stock should be invalid
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
    fun `should handle GraphQL query with invalid variable types`() {
      val query = TestDataBuilders.graphQLQuery(
        "query GetProduct(\$id: ID!) { product(id: \$id) { id name sku } }",
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

      val data = payload["data"]
      assertThat(data)
        .describedAs("Data should be null for invalid variable types")
        .isNotNull()
      val product = data["product"]
      assertThat(product)
        .describedAs("Data should be null for invalid variable types")
        .isNotNull()

    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `should handle concurrent GraphQL product mutations`() {
        val sku = uniqueSku()
        val mutation = TestDataBuilders.createProductMutation(
            name = uniqueName(),
            sku = sku,
            priceCents = 1000L,
            stock = 5
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
    fun `should handle large GraphQL product payloads`() {
        val largeMutation = TestDataBuilders.createProductMutation(
            name = "A".repeat(1000), // Test field length limits
            sku = uniqueSku(),
            priceCents = 1000L,
            stock = 5
        )

        val request = HttpRequest.POST("/graphql", largeMutation)
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    fun `should handle GraphQL product query with custom scalars`() {
        val queryWithCustomScalars = """
            query {
                products {
                    id
                    name
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
