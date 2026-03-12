package io.github.salomax.neotool.banking.infrastructure.security

import io.github.salomax.neotool.banking.domain.transaction.port.AccountMembershipValidationPort
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.UUID

/**
 * Security-backed account membership validator used by transaction attribution.
 */
@Singleton
@Requires(beans = [GraphQLServiceClient::class])
open class SecurityAccountMembershipValidationPort(
    private val graphQLServiceClient: GraphQLServiceClient,
) : AccountMembershipValidationPort {
    private val logger = KotlinLogging.logger {}

    override fun isUserActiveMemberOfAccount(
        userId: UUID,
        accountId: UUID,
    ): Boolean {
        val query =
            """
            query IsUserActiveMemberOfAccount(${'$'}userId: ID!, ${'$'}accountId: ID!) {
              isUserActiveMemberOfAccount(userId: ${'$'}userId, accountId: ${'$'}accountId)
            }
            """.trimIndent()

        val response =
            try {
                runBlocking {
                    graphQLServiceClient.query(
                        query = query,
                        variables =
                            mapOf(
                                "userId" to userId.toString(),
                                "accountId" to accountId.toString(),
                            ),
                        targetAudience = "apollo-router",
                    )
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "Security membership check failed due to upstream call error: userId=$userId, accountId=$accountId"
                }
                throw BusinessRuleException(
                    errorCode = CommonErrorCode.SERVICE_UNAVAILABLE,
                    field = "belongsToUserId",
                    parameters = mapOf("dependency" to "security"),
                    cause = e,
                )
            }

        if (!response.errors.isNullOrEmpty()) {
            logger.warn {
                "Security membership check returned GraphQL errors: userId=$userId, " +
                    "accountId=$accountId, errors=${response.errors}"
            }
            throw BusinessRuleException(
                errorCode = CommonErrorCode.SERVICE_UNAVAILABLE,
                field = "belongsToUserId",
                parameters = mapOf("dependency" to "security"),
            )
        }

        val result = response.data?.get("isUserActiveMemberOfAccount")
        return result as? Boolean
            ?: throw BusinessRuleException(
                errorCode = CommonErrorCode.SERVICE_UNAVAILABLE,
                field = "belongsToUserId",
                parameters = mapOf("dependency" to "security"),
            )
    }
}
