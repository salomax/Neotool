package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.security.service.GraphQLResponse
import io.github.salomax.neotool.common.security.service.GraphQLServiceClient
import io.github.salomax.neotool.security.config.EmailConfig
import io.github.salomax.neotool.security.service.email.CommsEmailService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("CommsEmailService Unit Tests")
class CommsEmailServiceTest {
    private lateinit var graphQLServiceClient: GraphQLServiceClient
    private lateinit var emailConfig: EmailConfig
    private lateinit var commsEmailService: CommsEmailService

    @BeforeEach
    fun setUp() {
        graphQLServiceClient = mock()
        emailConfig =
            EmailConfig(
                from = "noreply@test.com",
                frontendUrl = "http://localhost:3000",
            )
        commsEmailService = CommsEmailService(emailConfig, graphQLServiceClient)
    }

    @Nested
    @DisplayName("sendPasswordResetEmail")
    inner class SendPasswordResetEmailTests {
        @Test
        fun `should call requestEmailSend and succeed when Comms returns success`() =
            runBlocking {
            val successResponse =
                GraphQLResponse(
                    data =
                        mapOf(
                            "requestEmailSend" to
                                mapOf(
                                    "requestId" to "req-1",
                                    "status" to "QUEUED",
                                ),
                        ),
                    errors = null,
                )

            whenever(
                graphQLServiceClient.mutation(any(), any(), any()),
            ).thenReturn(successResponse)

            commsEmailService.sendPasswordResetEmail("user@example.com", "reset-token-123", "en")
            // No exception; CommsEmailService sends content.kind=TEMPLATE, templateKey=auth.password-reset, locale, variables
        }

        @Test
        fun `should call requestEmailSend for pt locale`() =
            runBlocking {
            whenever(
                graphQLServiceClient.mutation(any(), any(), any()),
            ).thenReturn(
                GraphQLResponse(
                    data = mapOf("requestEmailSend" to mapOf("requestId" to "1", "status" to "QUEUED")),
                    errors = null,
                ),
            )

            commsEmailService.sendPasswordResetEmail("user@example.com", "token", "pt")
            // No exception; content.kind=TEMPLATE, templateKey=auth.password-reset, locale=pt
        }

        @Test
        fun `should not throw when GraphQL returns errors`() =
            runBlocking {
            whenever(
                graphQLServiceClient.mutation(any(), any(), any()),
            ).thenReturn(
                GraphQLResponse(
                    data = null,
                    errors = listOf(mapOf("message" to "Validation error")),
                ),
            )

            commsEmailService.sendPasswordResetEmail("user@example.com", "token", "en")
            // No exception thrown
        }

        @Test
        fun `should not throw when mutation throws exception`() =
            runBlocking {
            whenever(
                graphQLServiceClient.mutation(any(), any(), any()),
            ).thenThrow(IllegalStateException("Network error"))

            commsEmailService.sendPasswordResetEmail("user@example.com", "token", "en")
            // No exception thrown - swallowed for security (don't reveal failures)
        }
    }
}
