package io.github.salomax.neotool.banking.service.transaction

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyAlreadyClaimedException
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRecord
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class IdempotencyService(
    private val repository: McpWriteIdempotencyRepository,
    objectMapper: ObjectMapper,
) {
    /**
     * Micronaut's shared [ObjectMapper] often omits empty collections (`NON_EMPTY`).
     * Idempotency replay must round-trip Kotlin DTOs with non-null `List` constructor
     * params (e.g. [io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO.tags]),
     * so storage always serializes empty collections.
     */
    private val storageMapper: ObjectMapper =
        objectMapper.copy().setSerializationInclusion(JsonInclude.Include.ALWAYS)

    /**
     * Stores the full JSON serialization of the mutation result [R].
     * Replays deserialize the same JSON back into [R], so callers must use a stable
     * result type that already contains every field their write tool needs to return.
     */
    @Transactional
    open fun <R : Any> execute(
        accountId: UUID,
        key: String?,
        operation: String,
        resultType: TypeReference<R>,
        block: () -> R,
    ): R {
        val normalizedKey = normalizeRequiredKey(key)
        require(operation.isNotBlank()) { "operation must not be blank" }

        val claim =
            try {
                repository.claim(accountId, normalizedKey, operation)
            } catch (_: McpWriteIdempotencyAlreadyClaimedException) {
                val stored =
                    repository.findByAccountIdAndIdempotencyKey(accountId, normalizedKey)
                        ?: throw IllegalStateException(
                            "Idempotency claim already exists for account=$accountId " +
                                "key=$normalizedKey but no row was found",
                        )
                validateStoredOperation(stored, operation)
                return deserialize(stored.resultJson, resultType)
            }

        val result = block()
        repository.storeResult(requireNotNull(claim.id), serialize(result))
        return result
    }

    private fun normalizeRequiredKey(key: String?): String {
        val normalized = key?.trim()
        if (normalized.isNullOrEmpty()) {
            throw ValidationException(
                errorCode = BankingErrorCode.MCP_IDEMPOTENCY_KEY_REQUIRED,
                field = "idempotencyKey",
            )
        }
        if (normalized.length > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "idempotencyKey",
                parameters = mapOf("maxLength" to IDEMPOTENCY_KEY_MAX_LENGTH),
            )
        }
        return normalized
    }

    private fun validateStoredOperation(
        stored: McpWriteIdempotencyRecord,
        requestedOperation: String,
    ) {
        if (stored.operation != requestedOperation) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "idempotencyKey",
                parameters =
                    mapOf(
                        "reason" to "Idempotency key was already used for a different operation",
                        "storedOperation" to stored.operation,
                        "requestedOperation" to requestedOperation,
                    ),
            )
        }
    }

    private fun <R : Any> deserialize(
        json: String,
        resultType: TypeReference<R>,
    ): R = storageMapper.readValue(json, resultType)

    private fun serialize(result: Any): String = storageMapper.writeValueAsString(result)

    companion object {
        private const val IDEMPOTENCY_KEY_MAX_LENGTH = 255
    }
}

inline fun <reified R : Any> IdempotencyService.execute(
    accountId: UUID,
    key: String?,
    operation: String,
    noinline block: () -> R,
): R =
    execute(
        accountId = accountId,
        key = key,
        operation = operation,
        resultType = object : TypeReference<R>() {},
        block = block,
    )

inline fun <reified R : Any> IdempotencyService.executeIfAgent(
    accountId: UUID,
    key: String?,
    operation: String,
    agentCallContext: AgentCallContext?,
    noinline block: () -> R,
): R =
    if (agentCallContext == null) {
        block()
    } else {
        execute(
            accountId = accountId,
            key = key,
            operation = operation,
            block = block,
        )
    }
