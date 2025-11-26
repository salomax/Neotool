package io.github.salomax.neotool.assistant.test.tool

import io.github.salomax.neotool.assistant.graphql.GraphQLClient
import io.github.salomax.neotool.assistant.graphql.GraphQLResponse
import io.github.salomax.neotool.assistant.llm.tool.CheckoutTool
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
class CheckoutToolTest {
    private lateinit var graphQLClient: GraphQLClient
    private lateinit var checkoutTool: CheckoutTool

    @BeforeEach
    fun setUp() {
        graphQLClient = mock()
        checkoutTool = CheckoutTool(graphQLClient)
    }

    @Test
    fun `should return checkout function definition`() {
        // Act
        val function = checkoutTool.getCheckoutFunction()

        // Assert
        assertThat(function.name).isEqualTo("checkout")
        assertThat(function.description).contains("Process a checkout")
        assertThat(function.parameters.required).contains("items", "totalPriceCents", "itemCount")
    }

    @Test
    fun `should execute checkout function`() {
        runBlocking {
            // Arrange
            val items: List<Map<String, Any>> =
                listOf(
                    mapOf(
                        "catalogItemId" to "item-1",
                        "name" to "USB-C Cable",
                        "priceCents" to 1500,
                        "quantity" to 2,
                    ),
                )
            val arguments =
                mapOf(
                    "items" to items,
                    "totalPriceCents" to 3000,
                    "itemCount" to 2,
                )
            val mockData =
                mapOf(
                    "checkout" to
                        mapOf(
                            "id" to "checkout-123",
                            "totalPriceCents" to 3000,
                            "itemCount" to 2,
                            "status" to "COMPLETED",
                        ),
                )
            val graphQLResponse =
                GraphQLResponse(
                    data = mockData,
                    errors = null,
                )

            whenever(graphQLClient.execute(any())).thenReturn(graphQLResponse)

            // Act
            val result = checkoutTool.execute("checkout", arguments)

            // Assert
            assertThat(result.success).isTrue()
            assertThat(result.data).isNotNull()
            verify(graphQLClient).execute(any())
        }
    }

    @Test
    fun `should return error when items parameter is missing`() {
        runBlocking {
            // Arrange
            val arguments =
                mapOf(
                    "totalPriceCents" to 3000,
                    "itemCount" to 2,
                )

            // Act
            val result = checkoutTool.execute("checkout", arguments)

            // Assert
            assertThat(result.success).isFalse()
            assertThat(result.error).contains("Missing 'items' parameter")
        }
    }
}
