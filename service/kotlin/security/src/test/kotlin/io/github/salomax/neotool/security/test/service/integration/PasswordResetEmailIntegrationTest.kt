package io.github.salomax.neotool.security.test.service.integration

import io.github.salomax.neotool.common.security.service.GraphQLResponse
import io.github.salomax.neotool.common.security.service.GraphQLServiceClient
import io.github.salomax.neotool.security.service.email.CommsEmailService
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@MicronautTest
@Tag("integration")
class PasswordResetEmailIntegrationTest : TestPropertyProvider {
    @Inject
    lateinit var commsEmailService: CommsEmailService

    @Inject
    lateinit var graphQLServiceClient: GraphQLServiceClient

    override fun getProperties(): MutableMap<String, String> {
        return mutableMapOf(
            "email.delivery" to "comms",
            "datasources.default.enabled" to "false",
            "jpa.default.enabled" to "false",
            "flyway.enabled" to "false",
            "kafka.enabled" to "false",
        )
    }

    @MockBean(GraphQLServiceClient::class)
    fun graphQLServiceClient(): GraphQLServiceClient {
        val client = mock<GraphQLServiceClient>()
        runBlocking {
            whenever(client.mutation(any(), any(), any())).thenReturn(
                GraphQLResponse(
                    data =
                        mapOf(
                            "requestEmailSend" to
                                mapOf(
                                    "requestId" to "req-123",
                                    "status" to "QUEUED",
                                ),
                        ),
                    errors = null,
                ),
            )
        }
        return client
    }

    @Test
    fun `send password reset email with template engine`() {
        val email = "user@example.com"
        val token = "test-reset-token-123"
        val locale = "pt-BR"

        commsEmailService.sendPasswordResetEmail(email, token, locale)

        val variablesCaptor = argumentCaptor<Map<String, Any>>()
        runBlocking {
            verify(graphQLServiceClient).mutation(any(), variablesCaptor.capture(), any())
        }
        val vars = variablesCaptor.firstValue
        val input = vars["input"] as? Map<*, *>
        val content = input?.get("content") as? Map<*, *>
        assertTrue(content?.get("kind") == "TEMPLATE", "Expected content.kind=TEMPLATE, got: ${content?.get("kind")}")
        assertTrue(content?.get("templateKey") == "auth.password-reset", "Expected templateKey=auth.password-reset, got: ${content?.get("templateKey")}")
        assertTrue(content?.get("locale") == "pt-BR", "Expected locale=pt-BR, got: ${content?.get("locale")}")
        val templateVars = content?.get("variables") as? Map<*, *>
        assertTrue(templateVars?.containsKey("resetUrl") == true, "Expected variables to contain resetUrl: $templateVars")
    }
}
