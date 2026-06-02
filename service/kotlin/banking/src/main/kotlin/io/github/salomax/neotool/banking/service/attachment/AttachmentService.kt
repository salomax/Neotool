package io.github.salomax.neotool.banking.service.attachment

import io.github.salomax.neotool.banking.client.assets.AssetsServiceClient
import io.github.salomax.neotool.banking.domain.attachment.HoldingAttachment
import io.github.salomax.neotool.banking.domain.attachment.MAX_ATTACHMENTS_PER_RECORD
import io.github.salomax.neotool.banking.domain.attachment.TransactionAttachment
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.repo.attachment.HoldingAttachmentRepository
import io.github.salomax.neotool.banking.repo.attachment.TransactionAttachmentRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Handles attaching and removing files from Transactions and InvestmentHoldings.
 *
 * Files live in the assets service under the [BANKING_ATTACHMENTS_NAMESPACE] namespace.
 * The frontend performs the 3-step upload (createAssetUpload → PUT to presigned URL →
 * confirmAssetUpload) and then calls [attachToTransaction] / [attachToHolding] with the assetId.
 */
@Singleton
open class AttachmentService(
    private val transactionAttachmentRepository: TransactionAttachmentRepository,
    private val holdingAttachmentRepository: HoldingAttachmentRepository,
    private val transactionRepository: TransactionRepository,
    private val holdingRepository: HoldingRepository,
    private val assetsServiceClient: AssetsServiceClient,
) {
    // ── Transaction attachments ───────────────────────────────────────────────

    fun listTransactionAttachments(
        transactionId: UUID,
        accountId: UUID,
    ): List<TransactionAttachment> = transactionAttachmentRepository.findByTransactionId(transactionId, accountId)

    @Transactional
    open fun attachToTransaction(
        transactionId: UUID,
        assetId: UUID,
        accountId: UUID,
    ): TransactionAttachment {
        transactionRepository.findById(transactionId, accountId)
            ?: throw NoSuchElementException("Transaction $transactionId not found")
        val count = transactionAttachmentRepository.countByTransactionId(transactionId, accountId)
        if (count >= MAX_ATTACHMENTS_PER_RECORD) {
            throw ValidationException(
                errorCode = BankingErrorCode.ATTACHMENT_LIMIT_REACHED,
                field = "assetId",
                parameters = mapOf("limit" to MAX_ATTACHMENTS_PER_RECORD),
            )
        }
        return transactionAttachmentRepository.save(
            TransactionAttachment(
                id = null,
                accountId = accountId,
                transactionId = transactionId,
                assetId = assetId,
            ),
        )
    }

    @Transactional
    open fun removeTransactionAttachment(
        attachmentId: UUID,
        accountId: UUID,
    ): Boolean {
        val found =
            transactionAttachmentRepository.findById(attachmentId, accountId)
                ?: throw NotFoundException(
                    errorCode = BankingErrorCode.ATTACHMENT_NOT_FOUND,
                    field = "attachmentId",
                    parameters = mapOf("attachmentId" to attachmentId.toString()),
                )
        val deleted = transactionAttachmentRepository.delete(attachmentId, accountId)
        if (deleted) {
            assetsServiceClient.deleteAsset(found.assetId, delegatedBy = accountId.toString())
        }
        return deleted
    }

    // ── Holding attachments ───────────────────────────────────────────────────

    fun listHoldingAttachments(
        holdingId: UUID,
        accountId: UUID,
    ): List<HoldingAttachment> = holdingAttachmentRepository.findByHoldingId(holdingId, accountId)

    @Transactional
    open fun attachToHolding(
        holdingId: UUID,
        assetId: UUID,
        accountId: UUID,
    ): HoldingAttachment {
        holdingRepository.findById(holdingId, accountId)
            ?: throw NoSuchElementException("Holding $holdingId not found")
        val count = holdingAttachmentRepository.countByHoldingId(holdingId, accountId)
        if (count >= MAX_ATTACHMENTS_PER_RECORD) {
            throw ValidationException(
                errorCode = BankingErrorCode.ATTACHMENT_LIMIT_REACHED,
                field = "assetId",
                parameters = mapOf("limit" to MAX_ATTACHMENTS_PER_RECORD),
            )
        }
        return holdingAttachmentRepository.save(
            HoldingAttachment(
                id = null,
                accountId = accountId,
                holdingId = holdingId,
                assetId = assetId,
            ),
        )
    }

    @Transactional
    open fun removeHoldingAttachment(
        attachmentId: UUID,
        accountId: UUID,
    ): Boolean {
        val found =
            holdingAttachmentRepository.findById(attachmentId, accountId)
                ?: throw NotFoundException(
                    errorCode = BankingErrorCode.ATTACHMENT_NOT_FOUND,
                    field = "attachmentId",
                    parameters = mapOf("attachmentId" to attachmentId.toString()),
                )
        val deleted = holdingAttachmentRepository.delete(attachmentId, accountId)
        if (deleted) {
            assetsServiceClient.deleteAsset(found.assetId, delegatedBy = accountId.toString())
        }
        return deleted
    }

    /**
     * Resolves asset metadata (mimeType, sizeBytes, filename, downloadUrl) for a list of asset IDs.
     * Called by the resolver when hydrating the attachments field.
     */
    fun resolveAssetMetadata(
        assetId: UUID,
        accountId: UUID,
        downloadTtlSeconds: Int = 3600,
    ) = assetsServiceClient.getAsset(assetId, downloadTtlSeconds, delegatedBy = accountId.toString())
}
