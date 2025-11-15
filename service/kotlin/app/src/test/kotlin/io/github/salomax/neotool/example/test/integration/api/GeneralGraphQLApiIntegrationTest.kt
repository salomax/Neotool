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
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.codec.CodecException
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.assertThrows
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@MicronautTest(startApplication = true)
@DisplayName("GraphQL API Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("generic")
@TestMethodOrder(MethodOrderer.Random::class) // Randomize test execution for better isolation
class GeneralGraphQLApiIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    // This file contains generic GraphQL functionality tests that are not specific to any domain entity.
    @Test
    fun `should handle invalid GraphQL query`() {
        val invalidQuery = mapOf(
            "query" to "invalid query syntax { products { id name }"
        )

        val request = HttpRequest.POST("/graphql", invalidQuery)
            .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()

        // Assert error response structure
        val payload: JsonNode = json.read(response)
        
        // Should have errors for invalid syntax
        val errors = payload["errors"]
        assertThat(errors)
          .describedAs("GraphQL errors must be present for invalid query")
          .isNotNull()
        assertThat(errors.isArray)
          .describedAs("Errors must be an array")
          .isTrue()
        assertThat(errors.size())
          .describedAs("Must have at least one error")
          .isGreaterThan(0)

        // Data should be null for invalid queries
        val data = payload["data"]
        assertThat(data)
          .describedAs("Data should be null for invalid queries")
          .isNull()
    }


    @Test
    fun `should handle GraphQL introspection query`() {
        val introspectionQuery = mapOf(
            "query" to """
                query IntrospectionQuery {
                    __schema {
                        queryType { name }
                        mutationType { name }
                        subscriptionType { name }
                        types {
                            ...FullType
                        }
                    }
                }
                fragment FullType on __Type {
                    kind
                    name
                    description
                }
            """.trimIndent()
        )

        val request = HttpRequest.POST("/graphql", introspectionQuery)
            .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()

        // Assert introspection response structure
        val payload: JsonNode = json.read(response)
        assertThat(payload["errors"])
          .describedAs("GraphQL errors must be absent for introspection")
          .isNull()

        val data = payload["data"]
        assertThat(data)
          .describedAs("GraphQL response must contain 'data'")
          .isNotNull()

        val schema = data["__schema"]
        assertThat(schema)
          .describedAs("Schema introspection data must be present")
          .isNotNull()

        // Validate schema structure
        val queryType = schema["queryType"]
        assertThat(queryType)
          .describedAs("Query type must be present")
          .isNotNull()
        assertThat(queryType["name"])
          .describedAs("Query type name must be present")
          .isNotNull()

        val mutationType = schema["mutationType"]
        assertThat(mutationType)
          .describedAs("Mutation type must be present")
          .isNotNull()

        val subscriptionType = schema["subscriptionType"]
        assertThat(subscriptionType)
          .describedAs("Subscription type must be present")
          .isNotNull()

        val types = schema["types"]
        assertThat(types)
          .describedAs("Types array must be present")
          .isNotNull()
        assertThat(types.isArray)
          .describedAs("Types must be an array")
          .isTrue()
    }

    @Test
    fun `should handle GraphQL query without content type`() {
        val query = TestDataBuilders.productsQuery()
        val request = HttpRequest.POST("/graphql", query)

        val response = httpClient.exchangeAsString(request)
        // Should either work or return 415 for unsupported media type
        assertThat(response.status.code).isIn(200, 415)
        
        if (response.status.code == 200) {
            // If successful, validate response structure
            val payload: JsonNode = json.read(response)
            val data = payload["data"]
            assertThat(data)
              .describedAs("GraphQL response must contain 'data'")
              .isNotNull()
        }
    }

    @Test
    fun `should handle empty GraphQL request`() {
        val emptyRequest = mapOf<String, Any>()
        val request = HttpRequest.POST("/graphql", emptyRequest)
            .contentType(MediaType.APPLICATION_JSON)

        val exception = assertThrows<HttpClientResponseException> {
            httpClient.exchangeAsString(request)
        }
        assert(exception.status == HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `should handle GraphQL query depth limits`() {
        val deepQuery = """
            query {
                products {
                    id
                    name
                    customers {
                        id
                        name
                        products {
                            id
                            name
                            customers {
                                id
                                name
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val request = HttpRequest.POST("/graphql", mapOf("query" to deepQuery))
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    
    fun `should handle GraphQL query complexity limits`() {
        val complexQuery = """
            query {
                products {
                    id
                    name
                    sku
                    priceCents
                    stock
                    createdAt
                    updatedAt
                }
                customers {
                    id
                    name
                    email
                    status
                    createdAt
                    updatedAt
                }
            }
        """.trimIndent()

        val request = HttpRequest.POST("/graphql", mapOf("query" to complexQuery))
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
          .shouldHaveNonEmptyBody()
    }

    @Test
    fun `should handle GraphQL batch requests`() {
        val batchRequest = listOf(
            mapOf("query" to TestDataBuilders.productsQuery()),
            mapOf("query" to TestDataBuilders.customersQuery())
        )

        val request = HttpRequest.POST("/graphql", batchRequest)
            .contentType(MediaType.APPLICATION_JSON)

      val exception = assertThrows<HttpClientResponseException> {
        httpClient.exchangeAsString(request)
      }
      // Should return 400 for validation errors
      assert(exception.status == HttpStatus.BAD_REQUEST)
    }



















    @Test
    
    fun `should handle GraphQL query with invalid JSON`() {
        val request = HttpRequest.POST("/graphql", "invalid json")
            .contentType(MediaType.APPLICATION_JSON)
        
        val exception = assertThrows<HttpClientResponseException> {
            httpClient.exchangeAsString(request)
        }
        assert(exception.status == HttpStatus.BAD_REQUEST)
    }

    @Test
    
    fun `should handle GraphQL query with malformed JSON`() {
        val request = HttpRequest.POST("/graphql", "{ invalid json }")
            .contentType(MediaType.APPLICATION_JSON)
        
        val exception = assertThrows<HttpClientResponseException> {
            httpClient.exchangeAsString(request)
        }
        assert(exception.status == HttpStatus.BAD_REQUEST)
    }

    @Test
    
    fun `should handle GraphQL query with empty query string`() {
        val request = HttpRequest.POST("/graphql", mapOf("query" to ""))
            .contentType(MediaType.APPLICATION_JSON)
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
        
        val body = response.body.get()
        assertThat(body).contains("errors")
    }

    @Test
    
    fun `should handle GraphQL query with null query`() {
        val request = HttpRequest.POST("/graphql", mapOf("query" to null))
            .contentType(MediaType.APPLICATION_JSON)

      val exception = assertThrows<HttpClientResponseException> {
        httpClient.exchangeAsString(request)
      }
      assert(exception.status == HttpStatus.BAD_REQUEST)
    }

    @Test
    
    fun `should handle GraphQL query with invalid content type`() {
        val request = HttpRequest.POST("/graphql", TestDataBuilders.productsQuery())
            .contentType(MediaType.TEXT_PLAIN)
        assertThrows<CodecException> {
            httpClient.exchangeAsString(request)
        }
    }

    @Test
    
    fun `should handle GraphQL query with custom headers`() {
        val request = HttpRequest.POST("/graphql", TestDataBuilders.productsQuery())
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Custom-Header", "custom-value")
            .header("Authorization", "Bearer test-token")
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    
    fun `should handle GraphQL query with rate limiting`() {
        val query = TestDataBuilders.productsQuery()
        
        // Send multiple requests quickly to test rate limiting
        repeat(100) {
            val request = HttpRequest.POST("/graphql", query)
                .contentType(MediaType.APPLICATION_JSON)
            
            try {
                httpClient.exchangeAsString(request)
            } catch (e: HttpClientResponseException) {
                if (e.status == HttpStatus.TOO_MANY_REQUESTS) {
                    // Rate limit hit - this is expected behavior
                    return
                }
            }
        }
    }

    @Test
    
    fun `should handle GraphQL query with timeout`() {
        val query = TestDataBuilders.productsQuery()
        
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Timeout", "1000") // 1 second timeout
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    
    fun `should handle GraphQL query with caching headers`() {
        val query = TestDataBuilders.productsQuery()
        
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cache-Control", "no-cache")
            .header("If-None-Match", "etag-value")
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    
    fun `should handle GraphQL query with compression`() {
        val query = TestDataBuilders.productsQuery()
        
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept-Encoding", "gzip, deflate")
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    
    fun `should handle GraphQL query with different HTTP methods`() {
        val query = TestDataBuilders.productsQuery()["query"]?.toString()

        // Test GET request
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val getRequest = HttpRequest.GET<Any>("/graphql?query=$encodedQuery")
        var exception = assertThrows<HttpClientResponseException> {
          httpClient.exchangeAsString(getRequest)
        }
        assert(exception.status == HttpStatus.METHOD_NOT_ALLOWED)

        // Test PUT request
        val putRequest = HttpRequest.PUT("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
        exception = assertThrows<HttpClientResponseException> {
          httpClient.exchangeAsString(putRequest)
        }
        assert(exception.status == HttpStatus.METHOD_NOT_ALLOWED)

    }

    @Test
    
    fun `should handle GraphQL query with different content types`() {
        // Test with different content types
        val contentTypes = listOf(
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_JSON_TYPE,
        )
        
        contentTypes.forEach { contentType ->
            val request = HttpRequest.POST("/graphql", TestDataBuilders.productsQuery())
                .contentType(contentType)
            httpClient.exchangeAsString(request).shouldBeSuccessful()
        }
    }

    @Test
    
    fun `should handle GraphQL query with different encodings`() {
        val query = TestDataBuilders.productsQuery()
        
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Content-Encoding", "utf-8")
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    
    fun `should handle GraphQL query with different languages`() {
        val query = TestDataBuilders.productsQuery()
        
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept-Language", "en-US,en;q=0.9")
        
        val response = httpClient.exchangeAsString(request)
        response
          .shouldBeSuccessful()
          .shouldBeJson()
    }

    @Test
    
    fun `should handle GraphQL query with different user agents`() {
        val query = TestDataBuilders.productsQuery()
        
        val request = HttpRequest.POST("/graphql", query)
            .contentType(MediaType.APPLICATION_JSON)
            .header("User-Agent", "GraphQL-Test-Client/1.0")
        
        val response = httpClient.exchangeAsString(request)
          .shouldBeSuccessful()
          .shouldBeJson()
    }
}
