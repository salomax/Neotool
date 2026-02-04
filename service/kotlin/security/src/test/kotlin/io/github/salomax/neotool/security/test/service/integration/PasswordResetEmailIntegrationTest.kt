package io.github.salomax.neotool.security.test.service.integration

import io.github.salomax.neotool.common.security.service.GraphQLResponse
import io.github.salomax.neotool.common.security.service.GraphQLServiceClient
import io.github.salomax.neotool.security.service.email.CommsEmailService
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Recording fake for GraphQLServiceClient so we can assert on mutation calls.
 * Micronaut injects a proxy, so we store the instance for test assertions.
 */
class RecordingGraphQLServiceClient(
    routerUrl: String = "",
    serviceTokenClient: io.github.salomax.neotool.common.security.service.ServiceTokenClient = mock(),
    httpClient: io.micronaut.http.client.HttpClient = mock(),
) : GraphQLServiceClient(routerUrl, serviceTokenClient, httpClient) {
    val mutationVariables = mutableListOf<Map<String, Any>?>()

    override suspend fun mutation(
        mutation: String,
        variables: Map<String, Any>?,
        targetAudience: String,
    ): GraphQLResponse {
        mutationVariables.add(variables)
        return GraphQLResponse(
            data =
                mapOf(
                    "requestEmailSend" to
                        mapOf(
                            "requestId" to "req-123",
                            "status" to "QUEUED",
                        ),
                ),
            errors = null,
        )
    }
}

@MicronautTest
@Tag("integration")
class PasswordResetEmailIntegrationTest : TestPropertyProvider {
    @Inject
    lateinit var commsEmailService: CommsEmailService

    @Inject
    lateinit var graphQLServiceClient: GraphQLServiceClient

    /** Set by @MockBean factory so test can assert on recorded mutation variables. */
    lateinit var recordingClient: RecordingGraphQLServiceClient
        private set

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
        recordingClient = RecordingGraphQLServiceClient()
        return recordingClient
    }

    @Test
    fun `send password reset email with template engine`() {
        val email = "user@example.com"
        val token = "test-reset-token-123"
        val locale = "pt-BR"

        commsEmailService.sendPasswordResetEmail(email, token, locale)

        assertTrue(recordingClient.mutationVariables.isNotEmpty(), "Expected at least one mutation call")
        val vars = recordingClient.mutationVariables.first()
        val input = vars?.get("input") as? Map<*, *>
        val content = input?.get("content") as? Map<*, *>
        assertTrue(content?.get("kind") == "TEMPLATE", "Expected content.kind=TEMPLATE, got: ${content?.get("kind")}")
        assertTrue(
            content?.get("templateKey") == "auth.password-reset",
            "Expected templateKey=auth.password-reset, got: ${content?.get("templateKey")}",
        )
        assertTrue(content?.get("locale") == "pt-BR", "Expected locale=pt-BR, got: ${content?.get("locale")}")
        val templateVars = content?.get("variables") as? Map<*, *>
        assertTrue(
            templateVars?.containsKey("resetUrl") == true,
            "Expected variables to contain resetUrl: $templateVars",
        )
    }
}
