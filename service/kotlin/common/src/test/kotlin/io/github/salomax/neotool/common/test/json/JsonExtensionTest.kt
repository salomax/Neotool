package io.github.salomax.neotool.common.test.json

import io.micronaut.http.HttpResponse
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

@DisplayName("JsonMapper.read Extension Tests")
class JsonExtensionTest {
    private lateinit var jsonMapper: JsonMapper

    @BeforeEach
    fun setUp() {
        jsonMapper = JsonMapper.createDefault()
    }

    @Nested
    @DisplayName("Empty Body Handling")
    inner class EmptyBodyTests {
        @Test
        fun `should throw IllegalStateException when body is empty`() {
            // Arrange
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.empty())

            // Act & Assert
            val exception =
                assertThrows<IllegalStateException> {
                    jsonMapper.read<Map<String, Any>>(response)
                }

            assertThat(exception.message).isEqualTo("Empty body")
        }
    }

    @Nested
    @DisplayName("String Body Handling")
    inner class StringBodyTests {
        @Test
        fun `should deserialize String body to Map`() {
            // Arrange
            val jsonString = """{"name":"Test","value":42}"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: Map<String, Any> = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["name"]).isEqualTo("Test")
            assertThat(result["value"]).isEqualTo(42)
        }

        @Test
        fun `should handle String body with nested objects`() {
            // Arrange
            val jsonString = """{"user":{"name":"John","age":30},"active":true}"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: Map<String, Any> = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            @Suppress("UNCHECKED_CAST")
            val user = result["user"] as Map<String, Any>
            assertThat(user["name"]).isEqualTo("John")
            assertThat(user["age"]).isEqualTo(30)
            assertThat(result["active"]).isEqualTo(true)
        }
    }

    @Nested
    @DisplayName("ByteArray Body Handling")
    inner class ByteArrayBodyTests {
        @Test
        fun `should deserialize ByteArray body to Map`() {
            // Arrange
            val jsonString = """{"name":"Test","value":42}"""
            val bytes = jsonString.toByteArray()
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(bytes))

            // Act
            val result: Map<String, Any> = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["name"]).isEqualTo("Test")
            assertThat(result["value"]).isEqualTo(42)
        }
    }

    @Nested
    @DisplayName("Object Body Handling")
    inner class ObjectBodyTests {
        @Test
        fun `should serialize and deserialize object body to Map`() {
            // Arrange
            val originalObject = mapOf("name" to "Test", "value" to 42)
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(originalObject))

            // Act
            val result: Map<String, Any> = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["name"]).isEqualTo("Test")
            assertThat(result["value"]).isEqualTo(42)
        }

        @Test
        fun `should handle object body with nested structures`() {
            // Arrange
            val originalObject =
                mapOf(
                    "name" to "Test",
                    "nested" to mapOf("inner" to "Inner"),
                )
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(originalObject))

            // Act
            val result: Map<String, Any> = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["name"]).isEqualTo("Test")
            @Suppress("UNCHECKED_CAST")
            val nested = result["nested"] as Map<String, Any>
            assertThat(nested["inner"]).isEqualTo("Inner")
        }
    }

    @Nested
    @DisplayName("JsonNode Type Handling")
    inner class JsonNodeTypeTests {
        @Test
        fun `should use special JsonNode path when type is JsonNode`() {
            // Arrange
            val jsonString = """{"name":"Test","value":42}"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["name"]).isNotNull()
            assertThat(result["name"].stringValue).isEqualTo("Test")
            assertThat(result["value"].intValue).isEqualTo(42)
        }

        @Test
        fun `should handle JsonNode with null values`() {
            // Arrange
            val jsonString = """{"data":null,"name":"Test"}"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["data"]).isNotNull()
            assertThat(result["data"].isNull).isTrue()
            assertThat(result["name"].stringValue).isEqualTo("Test")
        }

        @Test
        fun `should handle JsonNode with arrays`() {
            // Arrange
            val jsonString = """{"items":[1,2,3],"name":"Test"}"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["items"]).isNotNull()
            assertThat(result["items"].isArray).isTrue()
            assertThat(result["items"].size()).isEqualTo(3)
            assertThat(result["name"].stringValue).isEqualTo("Test")
        }

        @Test
        fun `should handle JsonNode with nested objects`() {
            // Arrange
            val jsonString = """{"user":{"name":"John","age":30},"active":true}"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["user"]).isNotNull()
            assertThat(result["user"]["name"].stringValue).isEqualTo("John")
            assertThat(result["user"]["age"].intValue).isEqualTo(30)
            assertThat(result["active"].booleanValue).isTrue()
        }

        @Test
        fun `should handle JsonNode from ByteArray body`() {
            // Arrange
            val jsonString = """{"name":"Test","value":42}"""
            val bytes = jsonString.toByteArray()
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(bytes))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["name"].stringValue).isEqualTo("Test")
            assertThat(result["value"].intValue).isEqualTo(42)
        }

        @Test
        fun `should handle JsonNode from object body`() {
            // Arrange
            val originalObject = mapOf("name" to "Test", "value" to 42)
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(originalObject))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["name"].stringValue).isEqualTo("Test")
            assertThat(result["value"].intValue).isEqualTo(42)
        }
    }

    @Nested
    @DisplayName("Regular Type Deserialization")
    inner class RegularTypeTests {
        @Test
        fun `should deserialize to List using JsonNode`() {
            // Arrange
            val jsonString = """["item1","item2"]"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.isArray).isTrue()
            assertThat(result.size()).isEqualTo(2)
            assertThat(result[0].stringValue).isEqualTo("item1")
            assertThat(result[1].stringValue).isEqualTo("item2")
        }

        @Test
        fun `should deserialize to primitive types`() {
            // Arrange
            val jsonString = """42"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: Int = jsonMapper.read(response)

            // Assert
            assertThat(result).isEqualTo(42)
        }

        @Test
        fun `should deserialize to String`() {
            // Arrange
            val jsonString = """"Hello World""""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: String = jsonMapper.read(response)

            // Assert
            assertThat(result).isEqualTo("Hello World")
        }

        @Test
        fun `should deserialize to Boolean`() {
            // Arrange
            val jsonString = """true"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: Boolean = jsonMapper.read(response)

            // Assert
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {
        @Test
        fun `should handle empty JSON object`() {
            // Arrange
            val jsonString = """{}"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: Map<String, Any> = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result).isEmpty()
        }

        @Test
        fun `should handle empty JSON array`() {
            // Arrange
            val jsonString = """[]"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.isArray).isTrue()
            assertThat(result.size()).isEqualTo(0)
        }

        @Test
        fun `should handle JSON with special characters in String body`() {
            // Arrange
            val jsonString = """{"message":"Hello \"World\"\nNew Line"}"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: Map<String, Any> = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result["message"]).isEqualTo("Hello \"World\"\nNew Line")
        }

        @Test
        fun `should handle large JSON payload`() {
            // Arrange
            val items = (1..1000).map { it }
            val jsonString = """[${items.joinToString(",")}]"""
            val response = mock<HttpResponse<*>>()
            whenever(response.body).thenReturn(Optional.of(jsonString))

            // Act
            val result: JsonNode = jsonMapper.read(response)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result.isArray).isTrue()
            assertThat(result.size()).isEqualTo(1000)
            assertThat(result[0].intValue).isEqualTo(1)
            assertThat(result[999].intValue).isEqualTo(1000)
        }
    }
}
