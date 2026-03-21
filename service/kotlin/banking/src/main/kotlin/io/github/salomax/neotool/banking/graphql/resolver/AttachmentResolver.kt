package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.attachment.AttachmentService
import io.github.salomax.neotool.banking.domain.attachment.HoldingAttachment
import io.github.salomax.neotool.banking.domain.attachment.TransactionAttachment
import io.github.salomax.neotool.banking.graphql.dto.HoldingAttachmentDTO
import io.github.salomax.neotool.banking.graphql.dto.TransactionAttachmentDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class AttachmentResolver(
    private val attachmentService: AttachmentService,
) {
    // ── Transaction attachments ───────────────────────────────────────────────

    fun transactionAttachments(transactionId: String, principal: RequestPrincipal): List<TransactionAttachmentDTO> {
        val accountId = requireAccountId(principal)
        return attachmentService
            .listTransactionAttachments(UUID.fromString(transactionId), accountId)
            .map { it.toDTO(accountId) }
    }

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
        val asset = runCatching {
            attachmentService.resolveAssetMetadata(assetId, accountId)
        }.getOrNull()
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
        val asset = runCatching {
            attachmentService.resolveAssetMetadata(assetId, accountId)
        }.getOrNull()
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

    private fun requireAccountId(principal: RequestPrincipal): UUID =
        requireNotNull(principal.currentAccountId) { "No active account in token" }
}
