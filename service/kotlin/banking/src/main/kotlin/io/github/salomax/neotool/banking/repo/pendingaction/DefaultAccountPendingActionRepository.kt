package io.github.salomax.neotool.banking.repo.pendingaction

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.model.pendingaction.AccountPendingActionEntity
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class DefaultAccountPendingActionRepository(
    private val entityRepository: AccountPendingActionEntityRepository,
    private val objectMapper: ObjectMapper,
) : AccountPendingActionRepository {
    override fun findByAccountAndStatus(
        accountId: UUID,
        status: AccountPendingActionStatus?,
        now: Instant,
    ): List<AccountPendingAction> =
        entityRepository.findByAccountIdAndOptionalStatus(accountId, status, now).map { it.toDomain() }

    override fun findById(
        id: UUID,
        accountId: UUID,
    ): AccountPendingAction? = entityRepository.findByIdAndAccountId(id, accountId)?.toDomain()

    override fun findPendingByEntityAndType(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
    ): AccountPendingAction? =
        entityRepository
            .findFirstByEntityTypeAndEntityIdAndTypeAndStatusOrderByCreatedAtDesc(
                entityType = entityType,
                entityId = entityId,
                type = type,
                status = AccountPendingActionStatus.PENDING,
            )
            ?.toDomain()

    override fun existsOpenForEntity(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
    ): Boolean =
        entityRepository.existsOpenForEntity(
            entityType = entityType,
            entityId = entityId,
            type = type.name,
        )

    override fun findDueForNotification(now: Instant): List<AccountPendingAction> =
        entityRepository.findDueForNotification(now).map { it.toDomain() }

    @Transactional
    override fun cancelByEntityAndType(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
        now: Instant,
    ): Boolean {
        val entity =
            entityRepository
                .findFirstByEntityTypeAndEntityIdAndTypeAndStatusOrderByCreatedAtDesc(
                    entityType = entityType,
                    entityId = entityId,
                    type = type,
                    status = AccountPendingActionStatus.PENDING,
                ) ?: return false
        entity.status = AccountPendingActionStatus.CANCELLED
        entity.cancelledAt = now
        entity.updatedAt = now
        entityRepository.update(entity)
        return true
    }

    @Transactional
    override fun dismissOpenForEntity(
        entityType: String,
        entityId: UUID,
        type: PendingActionType,
        now: Instant,
    ): Int =
        entityRepository.dismissOpenForEntity(
            entityType = entityType,
            entityId = entityId,
            type = type.name,
            now = now,
        )

    @Transactional
    override fun save(action: AccountPendingAction): AccountPendingAction {
        val entity =
            AccountPendingActionEntity(
                accountId = action.accountId,
                type = action.type,
                severity = action.severity,
                status = action.status,
                entityType = action.entityType,
                entityId = action.entityId,
                relatedTransactionId = action.relatedTransactionId,
                payloadJson = writePayload(action.payload),
                visibleFrom = action.visibleFrom,
                notifiedAt = action.notifiedAt,
                dismissedAt = action.dismissedAt,
                completedAt = action.completedAt,
                cancelledAt = action.cancelledAt,
                expiresAt = action.expiresAt,
                createdAt = action.createdAt,
                updatedAt = action.updatedAt,
            )
        return entityRepository.save(entity).toDomain()
    }

    @Transactional
    override fun update(action: AccountPendingAction): AccountPendingAction {
        val id = requireNotNull(action.id) { "AccountPendingAction id is required for update" }
        val entity =
            entityRepository.findById(id).orElseThrow {
                NoSuchElementException("AccountPendingAction $id not found")
            }
        entity.accountId = action.accountId
        entity.type = action.type
        entity.severity = action.severity
        entity.status = action.status
        entity.entityType = action.entityType
        entity.entityId = action.entityId
        entity.relatedTransactionId = action.relatedTransactionId
        entity.payloadJson = writePayload(action.payload)
        entity.visibleFrom = action.visibleFrom
        entity.notifiedAt = action.notifiedAt
        entity.dismissedAt = action.dismissedAt
        entity.completedAt = action.completedAt
        entity.cancelledAt = action.cancelledAt
        entity.expiresAt = action.expiresAt
        entity.updatedAt = action.updatedAt
        return entityRepository.save(entity).toDomain()
    }

    private fun writePayload(payload: Map<String, Any?>): String =
        objectMapper.writeValueAsString(payload.ifEmpty { emptyMap<String, Any?>() })

    private fun readPayload(json: String): Map<String, Any?> =
        when {
            json.isBlank() || json == "{}" -> emptyMap()
            else ->
                objectMapper.readValue(
                    json,
                    object : TypeReference<Map<String, Any?>>() {},
                )
        }

    private fun AccountPendingActionEntity.toDomain(): AccountPendingAction =
        AccountPendingAction(
            id = id,
            accountId = accountId,
            type = type,
            severity = severity,
            status = status,
            entityType = entityType,
            entityId = entityId,
            relatedTransactionId = relatedTransactionId,
            payload = readPayload(payloadJson),
            visibleFrom = visibleFrom,
            notifiedAt = notifiedAt,
            dismissedAt = dismissedAt,
            completedAt = completedAt,
            cancelledAt = cancelledAt,
            expiresAt = expiresAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
