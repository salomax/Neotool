package io.github.salomax.neotool.security.service.email

import io.github.salomax.neotool.common.security.service.GraphQLServiceClient
import io.github.salomax.neotool.security.config.EmailConfig
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID

/**
 * EmailService implementation that sends password reset emails via the Comms service
 * through the Apollo Router (service-to-service).
 *
 * Uses [GraphQLServiceClient] to call the supergraph mutation [requestEmailSend] with
 * content kind TEMPLATE: templateKey "auth.password-reset", locale, and variables.
 * Comms renders the template and sends the email. No Security → Comms dependency.
 *
 * Requires [graphql.router.url] and [security.service.id]/[security.service.secret]
 * to be configured when [email.delivery]=comms.
 */
@Singleton
class CommsEmailService(
    emailConfig: EmailConfig,
    private val graphQLServiceClient: GraphQLServiceClient,
) : EmailService(emailConfig) {
    override fun sendPasswordResetEmail(
        email: String,
        token: String,
        locale: String,
    ) {
        try {
            val resetUrl = buildResetUrl(token)

            val mutation =
                """
                mutation RequestEmailSend(${'$'}input: EmailSendRequestInput!) {
                  requestEmailSend(input: ${'$'}input) {
                    requestId
                    status
                  }
                }
                """.trimIndent()

            val variables =
                mapOf(
                    "input" to
                        mapOf(
                            "to" to email,
                            "content" to
                                mapOf(
                                    "kind" to "TEMPLATE",
                                    "format" to "HTML",
                                    "templateKey" to "auth.password-reset",
                                    "locale" to locale,
                                    "variables" to
                                        mapOf(
                                            "resetUrl" to resetUrl,
                                            "expiresInMinutes" to 60,
                                        ),
                                ),
                        ),
                )

            val response =
                runBlocking {
                    graphQLServiceClient.mutation(
                        mutation = mutation,
                        variables = variables,
                        targetAudience = "apollo-router",
                    )
                }

            if (!response.errors.isNullOrEmpty()) {
                logger.warn {
                    "requestEmailSend returned GraphQL errors for $email: ${response.errors}"
                }
                return
            }

            val data = response.data?.get("requestEmailSend") as? Map<*, *>
            if (data != null) {
                logger.info {
                    "Password reset email sent via Comms for $email: " +
                        "requestId=${data["requestId"]}, status=${data["status"]}"
                }
            } else {
                logger.warn { "requestEmailSend returned no data for $email" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send password reset email via Comms to: $email" }
            // Do not throw - same as AuthenticationService: return success for security
        }
    }

    override fun sendVerificationEmail(
        to: String,
        userName: String,
        token: UUID,
        expiresAt: Instant,
        locale: String,
    ) {
        try {
            val verificationLink = buildVerificationLink(token)
            val expirationHours = java.time.temporal.ChronoUnit.HOURS.between(Instant.now(), expiresAt).coerceAtLeast(0)

            val mutation =
                """
                mutation RequestEmailSend(${'$'}input: EmailSendRequestInput!) {
                  requestEmailSend(input: ${'$'}input) {
                    requestId
                    status
                  }
                }
                """.trimIndent()

            val variables =
                mapOf(
                    "input" to
                        mapOf(
                            "to" to to,
                            "content" to
                                mapOf(
                                    "kind" to "TEMPLATE",
                                    "format" to "HTML",
                                    "templateKey" to "auth.verify-email",
                                    "locale" to locale,
                                    "variables" to
                                        mapOf(
                                            "userName" to userName,
                                            "verificationLink" to verificationLink,
                                            "expirationHours" to expirationHours.toString(),
                                            "supportEmail" to "support@neotool.io",
                                            "logoUrl" to "https://neotool.io/logo.png",
                                        ),
                                ),
                        ),
                )

            val response =
                runBlocking {
                    graphQLServiceClient.mutation(
                        mutation = mutation,
                        variables = variables,
                        targetAudience = "apollo-router",
                    )
                }

            if (!response.errors.isNullOrEmpty()) {
                logger.warn {
                    "requestEmailSend (verification) returned GraphQL errors for $to: ${response.errors}"
                }
                return
            }

            val data = response.data?.get("requestEmailSend") as? Map<*, *>
            if (data != null) {
                logger.info {
                    "Verification email sent via Comms for $to: requestId=${data["requestId"]}, status=${data["status"]}"
                }
            } else {
                logger.warn { "requestEmailSend (verification) returned no data for $to" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send verification email via Comms to: $to" }
        }
    }

    private fun buildVerificationLink(token: UUID): String {
        val baseUrl = emailConfig.resolveFrontendUrl()
        return "$baseUrl/verify-email-link?token=$token"
    }
}
