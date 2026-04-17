package io.github.salomax.neotool.banking.graphql.attachment

import io.github.salomax.neotool.banking.service.attachment.AttachmentService
import io.github.salomax.neotool.banking.domain.attachment.HoldingAttachment
import io.github.salomax.neotool.banking.domain.attachment.TransactionAttachment
import io.github.salomax.neotool.banking.graphql.attachment.HoldingAttachmentDTO
import io.github.salomax.neotool.banking.graphql.attachment.TransactionAttachmentDTO
import io.github.salomax.neotool.banking.client.assets.AssetResult
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.util.UUID

@Singleton
class AttachmentResolver(
    private val attachmentService: AttachmentService,
) {
    private val log = LoggerFactory.getLogger(AttachmentResolver::class.java)

    // ── Transaction attachments ───────────────────────────────────────────────

    fun transactionAttachments(transactionId: String, principal: RequestPrincipal): List<TransactionAttachmentDTO> {
        val accountId = requireAccountId(principal)
        return attachmentService
            .listTransactionAttachments(UUID.fromString(transactionId), accountId)
            .map { it.toDTO(accountId) }
    }

    /**
     * Maps pre-loaded [TransactionAttachment] domain objects to DTOs.
     * Uses [TransactionAttachment.accountId] directly — no principal required, no permission check.
     * Safe to call from batch field resolvers where the parent resource was already authorized.
     */
    fun mapAttachmentsToDTOs(attachments: List<TransactionAttachment>): List<TransactionAttachmentDTO> =
        attachments.map { it.toDTO(it.accountId) }

    fun attachFileToTransaction(input: Map<String, Any?>, principal: RequestPrincipal): TransactionAttachmentDTO {
        val accountId = requireAccountId(principal)
        val transactionId = UUID.fromString(input["transactionId"] as String)
        val assetId = UUID.fromString(input["assetId"] as String)
        return attachmentService
            .attachToTransaction(transactionId, assetId, accountId)
            .toDTO(accountId)
    }

    fun removeTransactionAttachment(attachmentId: String, principal: RequestPrincipal): Boolean {
        val accountId = requireAccountId(principal)
        return attachmentService.removeTransactionAttachment(UUID.fromString(attachmentId), accountId)
    }

    // ── Holding attachments ───────────────────────────────────────────────────

    fun holdingAttachments(holdingId: String, principal: RequestPrincipal): List<HoldingAttachmentDTO> {
        val accountId = requireAccountId(principal)
        return attachmentService
            .listHoldingAttachments(UUID.fromString(holdingId), accountId)
            .map { it.toDTO(accountId) }
    }

    fun attachFileToHolding(input: Map<String, Any?>, principal: RequestPrincipal): HoldingAttachmentDTO {
        val accountId = requireAccountId(principal)
        val holdingId = UUID.fromString(input["holdingId"] as String)
        val assetId = UUID.fromString(input["assetId"] as String)
        return attachmentService
            .attachToHolding(holdingId, assetId, accountId)
            .toDTO(accountId)
    }

    fun removeHoldingAttachment(attachmentId: String, principal: RequestPrincipal): Boolean {
        val accountId = requireAccountId(principal)
        return attachmentService.removeHoldingAttachment(UUID.fromString(attachmentId), accountId)
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun TransactionAttachment.toDTO(accountId: UUID): TransactionAttachmentDTO {
        val asset = resolveAsset(assetId, accountId, id)
        return TransactionAttachmentDTO(
            id = id!!,
            transactionId = transactionId,
            assetId = assetId,
            filename = asset?.originalFilename,
            mimeType = asset?.mimeType,
            sizeBytes = asset?.sizeBytes,
            downloadUrl = asset?.downloadUrl,
            createdAt = createdAt,
        )
    }

    private fun HoldingAttachment.toDTO(accountId: UUID): HoldingAttachmentDTO {
        val asset = resolveAsset(assetId, accountId, id)
        return HoldingAttachmentDTO(
            id = id!!,
            holdingId = holdingId,
            assetId = assetId,
            filename = asset?.originalFilename,
            mimeType = asset?.mimeType,
            sizeBytes = asset?.sizeBytes,
            downloadUrl = asset?.downloadUrl,
            createdAt = createdAt,
        )
    }

    private fun resolveAsset(assetId: UUID, accountId: UUID, attachmentId: UUID?): AssetResult? =
        runCatching {
            attachmentService.resolveAssetMetadata(assetId, accountId)
        }.onFailure { e ->
            log.warn("Failed to resolve asset metadata for assetId={} attachmentId={}: {}", assetId, attachmentId, e.message)
        }.getOrNull()

    private fun requireAccountId(principal: RequestPrincipal): UUID =
        requireNotNull(principal.currentAccountId) { "No active account in token" }
}
