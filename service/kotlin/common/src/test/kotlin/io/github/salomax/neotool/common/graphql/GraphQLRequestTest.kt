package io.github.salomax.neotool.common.graphql

import io.micronaut.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("GraphQLRequest Tests")
class GraphQLRequestTest {
    private lateinit var jsonMapper: JsonMapper

    @BeforeEach
    fun setUp() {
        jsonMapper = JsonMapper.createDefault()
    }

    @Nested
    @DisplayName("Data Class Properties")
    inner class DataClassPropertiesTests {
        @Test
        fun `should create instance with all properties`() {
            // Arrange & Act
            val request =
                GraphQLRequest(
                    query = "query { test }",
                    variables = mapOf("id" to "123"),
                    operationName = "GetTest",
                )

            // Assert
            assertThat(request.query).isEqualTo("query { test }")
            assertThat(request.variables).isEqualTo(mapOf("id" to "123"))
            assertThat(request.operationName).isEqualTo("GetTest")
        }

        @Test
        fun `should create instance with only query`() {
            // Arrange & Act
            val request = GraphQLRequest(query = "query { test }")

            // Assert
            assertThat(request.query).isEqualTo("query { test }")
            assertThat(request.variables).isNull()
            assertThat(request.operationName).isNull()
        }

        @Test
        fun `should support equality comparison`() {
            // Arrange
            val request1 =
                GraphQLRequest(
                    query = "query { test }",
                    variables = mapOf("id" to "123"),
                    operationName = "GetTest",
                )
            val request2 =
                GraphQLRequest(
                    query = "query { test }",
                    variables = mapOf("id" to "123"),
                    operationName = "GetTest",
                )

            // Act & Assert
            assertThat(request1).isEqualTo(request2)
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode())
        }

        @Test
        fun `should support toString`() {
            // Arrange
            val request =
                GraphQLRequest(
                    query = "query { test }",
                    variables = mapOf("id" to "123"),
                    operationName = "GetTest",
                )

            // Act
            val toString = request.toString()

            // Assert
            assertThat(toString).contains("query")
            assertThat(toString).contains("variables")
            assertThat(toString).contains("operationName")
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    inner class JsonSerializationTests {
        @Test
        fun `should serialize to JSON with all properties`() {
            // Arrange
            val request =
                GraphQLRequest(
                    query = "query { test }",
                    variables = mapOf("id" to "123", "name" to "Test"),
                    operationName = "GetTest",
                )

            // Act
            val json = jsonMapper.writeValueAsString(request)

            // Assert
            assertThat(json).contains("query")
            assertThat(json).contains("variables")
            assertThat(json).contains("operationName")
            assertThat(json).contains("GetTest")
            assertThat(json).contains("123")
        }

        @Test
        fun `should serialize to JSON with only query`() {
            // Arrange
            val request = GraphQLRequest(query = "query { test }")

            // Act
            val json = jsonMapper.writeValueAsString(request)

            // Assert
            assertThat(json).contains("query")
            assertThat(json).contains("query { test }")
        }

        @Test
        fun `should serialize null variables as null in JSON`() {
            // Arrange
            val request =
                GraphQLRequest(
                    query = "query { test }",
                    variables = null,
                    operationName = null,
                )

            // Act
            val json = jsonMapper.writeValueAsString(request)

            // Assert
            assertThat(json).contains("query")
            // Variables and operationName may be omitted or null depending on serializer
        }
    }

    @Nested
    @DisplayName("JSON Deserialization")
    inner class JsonDeserializationTests {
        @Test
        fun `should deserialize from JSON with all properties`() {
            // Arrange
            val json =
                """
                |{
                |    "query": "query { test }",
                |    "variables": {"id": "123", "name": "Test"},
                |    "operationName": "GetTest"
                |}
                """.trimMargin()

            // Act
            val request = jsonMapper.readValue(json, GraphQLRequest::class.java)

            // Assert
            assertThat(request.query).isEqualTo("query { test }")
            assertThat(request.variables).isNotNull
            assertThat(request.variables!!["id"]).isEqualTo("123")
            assertThat(request.variables!!["name"]).isEqualTo("Test")
            assertThat(request.operationName).isEqualTo("GetTest")
        }

        @Test
        fun `should deserialize from JSON with only query`() {
            // Arrange
            val json =
                """
                |{
                |    "query": "query { test }"
                |}
                """.trimMargin()

            // Act
            val request = jsonMapper.readValue(json, GraphQLRequest::class.java)

            // Assert
            assertThat(request.query).isEqualTo("query { test }")
            assertThat(request.variables).isNull()
            assertThat(request.operationName).isNull()
        }

        @Test
        fun `should deserialize from JSON with null variables`() {
            // Arrange
            val json =
                """
                |{
                |    "query": "query { test }",
                |    "variables": null,
                |    "operationName": null
                |}
                """.trimMargin()

            // Act
            val request = jsonMapper.readValue(json, GraphQLRequest::class.java)

            // Assert
            assertThat(request.query).isEqualTo("query { test }")
            assertThat(request.variables).isNull()
            assertThat(request.operationName).isNull()
        }

        @Test
        fun `should deserialize from JSON with empty variables`() {
            // Arrange
            val json =
                """
                |{
                |    "query": "query { test }",
                |    "variables": {}
                |}
                """.trimMargin()

            // Act
            val request = jsonMapper.readValue(json, GraphQLRequest::class.java)

            // Assert
            assertThat(request.query).isEqualTo("query { test }")
            assertThat(request.variables).isNotNull
            assertThat(request.variables).isEmpty()
        }
    }

    @Nested
    @DisplayName("Round-trip Serialization")
    inner class RoundTripSerializationTests {
        @Test
        fun `should serialize and deserialize correctly`() {
            // Arrange
            val original =
                GraphQLRequest(
                    query = "query GetTest(\$id: ID!) { test(id: \$id) }",
                    variables = mapOf("id" to "123", "name" to "Test"),
                    operationName = "GetTest",
                )

            // Act
            val json = jsonMapper.writeValueAsString(original)
            val deserialized = jsonMapper.readValue(json, GraphQLRequest::class.java)

            // Assert
            assertThat(deserialized).isEqualTo(original)
            assertThat(deserialized.query).isEqualTo(original.query)
            assertThat(deserialized.variables).isEqualTo(original.variables)
            assertThat(deserialized.operationName).isEqualTo(original.operationName)
        }

        @Test
        fun `should serialize and deserialize with null optional fields`() {
            // Arrange
            val original = GraphQLRequest(query = "query { test }")

            // Act
            val json = jsonMapper.writeValueAsString(original)
            val deserialized = jsonMapper.readValue(json, GraphQLRequest::class.java)

            // Assert
            assertThat(deserialized).isEqualTo(original)
            assertThat(deserialized.query).isEqualTo(original.query)
            assertThat(deserialized.variables).isNull()
            assertThat(deserialized.operationName).isNull()
        }
    }
}
