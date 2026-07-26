package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.McpWriteIdempotencyEntity
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.exception.ConstraintViolationException
import org.postgresql.util.PSQLException
import java.time.Instant
import java.util.UUID

@Singleton
open class DefaultMcpWriteIdempotencyRepository(
    private val entityRepository: McpWriteIdempotencyEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : McpWriteIdempotencyRepository {
    /**
     * Must run inside the caller's transaction.
     *
     * The atomic "claim or replay" guarantee depends on the later result update committing in the
     * same transaction that claimed the row, so callers should go through [IdempotencyService.execute].
     */
    override fun claim(
        accountId: UUID,
        idempotencyKey: String,
        operation: String,
    ): McpWriteIdempotencyRecord {
        try {
            @Suppress("UNCHECKED_CAST")
            val inserted =
                entityManager
                    .createNativeQuery(
                        """
                        INSERT INTO banking.mcp_write_idempotency (
                            account_id,
                            idempotency_key,
                            operation,
                            result,
                            created_at
                        ) VALUES (
                            CAST(:accountId AS uuid),
                            :idempotencyKey,
                            :operation,
                            CAST(:resultJson AS jsonb),
                            :createdAt
                        )
                        ON CONFLICT (account_id, idempotency_key) DO NOTHING
                        RETURNING id, account_id, idempotency_key, operation, result, created_at
                        """.trimIndent(),
                        McpWriteIdempotencyEntity::class.java,
                    ).setParameter("accountId", accountId.toString())
                    .setParameter("idempotencyKey", idempotencyKey)
                    .setParameter("operation", operation)
                    // This placeholder JSON is never observable: concurrent readers block on the
                    // uncommitted row, and post-commit readers see the final serialized result.
                    .setParameter("resultJson", "{}")
                    .setParameter("createdAt", Instant.now())
                    .resultList
                    .firstOrNull() as? McpWriteIdempotencyEntity

            if (inserted != null) {
                return inserted.toRecord()
            }

            throw McpWriteIdempotencyAlreadyClaimedException(accountId, idempotencyKey, operation)
        } catch (e: Exception) {
            if (isDuplicateClaim(e)) {
                throw McpWriteIdempotencyAlreadyClaimedException(accountId, idempotencyKey, operation, e)
            }
            throw e
        }
    }

    override fun findByAccountIdAndIdempotencyKey(
        accountId: UUID,
        idempotencyKey: String,
    ): McpWriteIdempotencyRecord? =
        entityRepository.findByAccountIdAndIdempotencyKey(accountId, idempotencyKey)?.toRecord()

    @Transactional
    override fun storeResult(
        id: UUID,
        resultJson: String,
    ): McpWriteIdempotencyRecord {
        val entity =
            entityRepository.findById(id).orElseThrow {
                NoSuchElementException("McpWriteIdempotencyEntity $id not found")
            }
        entity.resultJson = resultJson
        return entityRepository.save(entity).toRecord()
    }

    @Transactional
    override fun deleteCreatedBefore(cutoff: Instant): Int = entityRepository.deleteCreatedBefore(cutoff)

    private fun isDuplicateClaim(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is ConstraintViolationException -> {
                    if (current.constraintName == DUPLICATE_CLAIM_CONSTRAINT || current.sqlState == UNIQUE_SQL_STATE) {
                        return true
                    }
                }
                is PSQLException -> {
                    if (
                        current.message?.contains(DUPLICATE_CLAIM_CONSTRAINT) == true ||
                        current.sqlState == UNIQUE_SQL_STATE
                    ) {
                        return true
                    }
                }
            }
            current = current.cause
        }
        return false
    }

    private fun McpWriteIdempotencyEntity.toRecord(): McpWriteIdempotencyRecord =
        McpWriteIdempotencyRecord(
            id = id,
            accountId = accountId,
            idempotencyKey = idempotencyKey,
            operation = operation,
            resultJson = resultJson,
            createdAt = createdAt,
        )

    companion object {
        private const val DUPLICATE_CLAIM_CONSTRAINT = "uq_mcp_write_idempotency_account_key"
        private const val UNIQUE_SQL_STATE = "23505"
    }
}
