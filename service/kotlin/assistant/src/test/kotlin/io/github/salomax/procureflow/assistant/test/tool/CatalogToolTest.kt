package io.github.salomax.neotool.assistant.test.tool

import io.github.salomax.neotool.assistant.graphql.GraphQLClient
import io.github.salomax.neotool.assistant.graphql.GraphQLResponse
import io.github.salomax.neotool.assistant.llm.tool.CatalogTool
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@MicronautTest
class CatalogToolTest {
    private lateinit var graphQLClient: GraphQLClient
    private lateinit var catalogTool: CatalogTool

    @BeforeEach
    fun setUp() {
        graphQLClient = mock()
        catalogTool = CatalogTool(graphQLClient)
    }

    @Test
    fun `should return search function definition`() {
        // Act
        val function = catalogTool.getSearchFunction()

        // Assert
        assertThat(function.name).isEqualTo("searchCatalogItems")
        assertThat(function.description).contains("Search for catalog items")
        assertThat(function.parameters.required).contains("query")
    }

    @Test
    fun `should execute searchCatalogItems function`() {
        runBlocking {
            // Arrange
            val arguments = mapOf("query" to "USB-C")
            val mockData =
                mapOf(
                    "searchCatalogItems" to
                        listOf(
                            mapOf("id" to "1", "name" to "USB-C Cable - 1m"),
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("searchCatalogItems", arguments)

            // Assert
            assertThat(result.success).isTrue()
            assertThat(result.data).isNotNull()
            verify(graphQLClient).execute(any())
        }
    }

    @Test
    fun `should return error when search query is missing`() {
        runBlocking {
            // Arrange
            val arguments = emptyMap<String, Any?>()

            // Act
            val result = catalogTool.execute("searchCatalogItems", arguments)

            // Assert
            assertThat(result.success).isFalse()
            assertThat(result.error).contains("Missing 'query' parameter")
        }
    }

    @Test
    fun `should execute catalogItem function`() {
        runBlocking {
            // Arrange
            val arguments = mapOf("id" to "item-123")
            val mockData =
                mapOf(
                    "catalogItem" to
                        mapOf(
                            "id" to "item-123",
                            "name" to "USB-C Cable",
                            "priceCents" to 1500,
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("catalogItem", arguments)

            // Assert
            assertThat(result.success).isTrue()
            assertThat(result.data).isNotNull()
        }
    }

    @Test
    fun `should transform priceCents to formatted price in searchCatalogItems`() {
        runBlocking {
            // Arrange
            val arguments = mapOf("query" to "USB-C")
            val mockData =
                mapOf(
                    "searchCatalogItems" to
                        listOf(
                            mapOf(
                                "id" to "1",
                                "name" to "USB-C Cable - 1m",
                                "category" to "MATERIAL",
                                "priceCents" to 1500,
                                "status" to "ACTIVE",
                            ),
                            mapOf(
                                "id" to "2",
                                "name" to "USB-C Cable - 2m",
                                "category" to "MATERIAL",
                                "priceCents" to 2999,
                                "status" to "ACTIVE",
                            ),
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("searchCatalogItems", arguments)

            // Assert
            assertThat(result.success).isTrue()
            assertThat(result.data).isNotNull()

            @Suppress("UNCHECKED_CAST")
            val items = result.data as? List<Map<String, Any?>>
            assertThat(items).isNotNull()
            assertThat(items!!).hasSize(2)

            // Verify first item
            val item1 = items[0]
            assertThat(item1["priceCents"]).isEqualTo(1500)
            assertThat(item1["price"]).isEqualTo("$15.00")
            assertThat(item1["id"]).isEqualTo("1")
            assertThat(item1["name"]).isEqualTo("USB-C Cable - 1m")

            // Verify second item
            val item2 = items[1]
            assertThat(item2["priceCents"]).isEqualTo(2999)
            assertThat(item2["price"]).isEqualTo("$29.99")
        }
    }

    @Test
    fun `should transform priceCents to formatted price in catalogItem`() {
        runBlocking {
            // Arrange
            val arguments = mapOf("id" to "item-123")
            val mockData =
                mapOf(
                    "catalogItem" to
                        mapOf(
                            "id" to "item-123",
                            "name" to "USB-C Cable",
                            "category" to "MATERIAL",
                            "priceCents" to 1500,
                            "status" to "ACTIVE",
                            "description" to "High quality USB-C cable",
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("catalogItem", arguments)

            // Assert
            assertThat(result.success).isTrue()
            assertThat(result.data).isNotNull()

            @Suppress("UNCHECKED_CAST")
            val item = result.data as? Map<String, Any?>
            assertThat(item).isNotNull()
            assertThat(item!!["priceCents"]).isEqualTo(1500)
            assertThat(item["price"]).isEqualTo("$15.00")
            assertThat(item["id"]).isEqualTo("item-123")
            assertThat(item["name"]).isEqualTo("USB-C Cable")
        }
    }

    @Test
    fun `should transform priceCents to formatted price in saveCatalogItem`() {
        runBlocking {
            // Arrange
            val arguments =
                mapOf(
                    "name" to "New Item",
                    "category" to "MATERIAL",
                    "priceCents" to 2500,
                    "status" to "ACTIVE",
                    "description" to "A new item",
                )
            val mockData =
                mapOf(
                    "saveCatalogItem" to
                        mapOf(
                            "id" to "new-item-123",
                            "name" to "New Item",
                            "category" to "MATERIAL",
                            "priceCents" to 2500,
                            "status" to "ACTIVE",
                            "description" to "A new item",
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("saveCatalogItem", arguments)

            // Assert
            assertThat(result.success).isTrue()
            assertThat(result.data).isNotNull()

            @Suppress("UNCHECKED_CAST")
            val item = result.data as? Map<String, Any?>
            assertThat(item).isNotNull()
            assertThat(item!!["priceCents"]).isEqualTo(2500)
            assertThat(item["price"]).isEqualTo("$25.00")
        }
    }

    @Test
    fun `should format different price values correctly`() {
        runBlocking {
            // Arrange
            val testCases =
                listOf(
                    0 to "$0.00",
                    1 to "$0.01",
                    99 to "$0.99",
                    100 to "$1.00",
                    1500 to "$15.00",
                    9999 to "$99.99",
                    10000 to "$100.00",
                    123456 to "$1,234.56",
                    999999 to "$9,999.99",
                )

            for ((cents, expectedPrice) in testCases) {
                val arguments = mapOf("id" to "item-$cents")
                val mockData =
                    mapOf(
                        "catalogItem" to
                            mapOf(
                                "id" to "item-$cents",
                                "name" to "Test Item",
                                "priceCents" to cents,
                            ),
                    )
                val graphQLResponse =
                    GraphQLResponse(
                        data = mockData,
                        errors = null,
                    )

                whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

                // Act
                val result = catalogTool.execute("catalogItem", arguments)

                // Assert
                assertThat(result.success).isTrue()

                @Suppress("UNCHECKED_CAST")
                val item = result.data as? Map<String, Any?>
                assertThat(item).isNotNull()
                assertThat(item!!["price"]).isEqualTo(expectedPrice)
                    .`as`("Price formatting for $cents cents should be $expectedPrice")
            }
        }
    }

    @Test
    fun `should handle priceCents as string`() {
        runBlocking {
            // Arrange
            val arguments = mapOf("id" to "item-123")
            val mockData =
                mapOf(
                    "catalogItem" to
                        mapOf(
                            "id" to "item-123",
                            "name" to "Test Item",
                            // String instead of number
                            "priceCents" to "1500",
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("catalogItem", arguments)

            // Assert
            assertThat(result.success).isTrue()

            @Suppress("UNCHECKED_CAST")
            val item = result.data as? Map<String, Any?>
            assertThat(item).isNotNull()
            assertThat(item!!["price"]).isEqualTo("$15.00")
        }
    }

    @Test
    fun `should handle missing priceCents gracefully`() {
        runBlocking {
            // Arrange
            val arguments = mapOf("id" to "item-123")
            val mockData =
                mapOf(
                    "catalogItem" to
                        mapOf(
                            "id" to "item-123",
                            "name" to "Test Item",
                            // No priceCents field
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("catalogItem", arguments)

            // Assert
            assertThat(result.success).isTrue()

            @Suppress("UNCHECKED_CAST")
            val item = result.data as? Map<String, Any?>
            assertThat(item).isNotNull()
            // Should format missing priceCents as $0.00
            assertThat(item!!["price"]).isEqualTo("$0.00")
        }
    }

    @Test
    fun `should handle null priceCents gracefully`() {
        runBlocking {
            // Arrange
            val arguments = mapOf("id" to "item-123")
            val mockData =
                mapOf(
                    "catalogItem" to
                        mapOf(
                            "id" to "item-123",
                            "name" to "Test Item",
                            "priceCents" to null,
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("catalogItem", arguments)

            // Assert
            assertThat(result.success).isTrue()

            @Suppress("UNCHECKED_CAST")
            val item = result.data as? Map<String, Any?>
            assertThat(item).isNotNull()
            // Should format null as $0.00
            assertThat(item!!["price"]).isEqualTo("$0.00")
        }
    }

    @Test
    fun `should preserve all other fields when transforming`() {
        runBlocking {
            // Arrange
            val arguments = mapOf("id" to "item-123")
            val mockData =
                mapOf(
                    "catalogItem" to
                        mapOf(
                            "id" to "item-123",
                            "name" to "USB-C Cable",
                            "category" to "MATERIAL",
                            "priceCents" to 1500,
                            "status" to "ACTIVE",
                            "description" to "High quality cable",
                            "createdAt" to "2024-01-01T00:00:00Z",
                            "updatedAt" to "2024-01-02T00:00:00Z",
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = catalogTool.execute("catalogItem", arguments)

            // Assert
            assertThat(result.success).isTrue()

            @Suppress("UNCHECKED_CAST")
            val item = result.data as? Map<String, Any?>
            assertThat(item).isNotNull()

            // Verify all original fields are preserved
            assertThat(item!!["id"]).isEqualTo("item-123")
            assertThat(item["name"]).isEqualTo("USB-C Cable")
            assertThat(item["category"]).isEqualTo("MATERIAL")
            assertThat(item["status"]).isEqualTo("ACTIVE")
            assertThat(item["description"]).isEqualTo("High quality cable")
            assertThat(item["createdAt"]).isEqualTo("2024-01-01T00:00:00Z")
            assertThat(item["updatedAt"]).isEqualTo("2024-01-02T00:00:00Z")

            // Verify price transformation
            assertThat(item["priceCents"]).isEqualTo(1500)
            assertThat(item["price"]).isEqualTo("$15.00")
        }
    }
}
