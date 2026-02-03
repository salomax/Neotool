package io.github.salomax.neotool.security.service.email

import io.github.salomax.neotool.common.security.service.GraphQLServiceClient
import io.github.salomax.neotool.security.config.EmailConfig
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Factory for creating EmailService instances.
 *
 * Chooses implementation based on [email.delivery]:
 * - `mock` (default): [MockEmailService] logs emails to the console/logs.
 * - `comms`: [CommsEmailService] sends via Apollo Router to Comms service (requires
 *   [graphql.router.url], [security.service.id], [security.service.secret]).
 */
@Factory
class EmailServiceFactory(
    private val emailConfig: EmailConfig,
    private val graphQLServiceClient: GraphQLServiceClient,
    @param:Value("\${email.delivery:mock}") private val emailDelivery: String,
) {
    private val logger = KotlinLogging.logger {}

    @Singleton
    fun emailService(): EmailService {
        return when (emailDelivery) {
            "comms" -> {
                logger.info { "Using CommsEmailService for email delivery (via Apollo Router)" }
                CommsEmailService(emailConfig, graphQLServiceClient)
            }
            else -> {
                logger.info { "Using MockEmailService for email delivery (emails logged to console)" }
                MockEmailService(emailConfig)
            }
        }
    }
}
