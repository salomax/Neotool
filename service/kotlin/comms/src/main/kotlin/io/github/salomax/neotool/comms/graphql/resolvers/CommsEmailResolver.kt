package io.github.salomax.neotool.comms.graphql.resolvers

import graphql.GraphQLException
import io.github.salomax.neotool.comms.email.dto.EmailSendRequest
import io.github.salomax.neotool.comms.email.dto.EmailSendResult
import io.github.salomax.neotool.comms.email.service.EmailSendService
import io.github.salomax.neotool.common.graphql.InputValidator
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import jakarta.inject.Singleton
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Singleton
class CommsEmailResolver(
    private val emailSendService: EmailSendService,
    private val jsonMapper: JsonMapper,
    private val inputValidator: InputValidator,
) {
    fun requestEmailSend(input: Map<String, Any?>): EmailSendResult {
        val request = mapToEmailSendRequest(input)
        inputValidator.validate(request)
        return emailSendService.requestSend(request)
    }

    private fun mapToEmailSendRequest(input: Map<String, Any?>): EmailSendRequest {
        return try {
            val bytes = jsonMapper.writeValueAsBytes(input)
            jsonMapper.readValue(bytes, Argument.of(EmailSendRequest::class.java))
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse EmailSendRequest from input: $input" }
            throw GraphQLException("Invalid input format: ${e.message}")
        }
    }
}
