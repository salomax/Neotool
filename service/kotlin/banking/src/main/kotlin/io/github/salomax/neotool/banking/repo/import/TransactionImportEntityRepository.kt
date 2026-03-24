package io.github.salomax.neotool.banking.repo.import

import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.model.import.TransactionImportEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for transaction-import rows in banking.document_import_jobs.
 * All finders scope by account_id (tenant isolation).
 */
@Repository
interface TransactionImportEntityRepository : JpaRepository<TransactionImportEntity, UUID> {
    fun findByIdAndAccountIdAndJobKind(
        id: UUID,
        accountId: UUID,
        jobKind: String,
    ): TransactionImportEntity?

    fun findByFinancialAccountIdAndFileHashAndJobKind(
        financialAccountId: UUID,
        fileHash: String,
        jobKind: String,
    ): TransactionImportEntity?

    @Query(
        """
        SELECT *
          FROM banking.document_import_jobs
         WHERE account_id = :accountId
           AND file_hash = :fileHash
           AND job_kind = :jobKind
         ORDER BY created_at DESC
         LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findLatestByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
        jobKind: String,
    ): TransactionImportEntity?

    fun findByFinancialAccountIdAndAccountIdAndJobKindOrderByCreatedAtDesc(
        financialAccountId: UUID,
        accountId: UUID,
        jobKind: String,
    ): List<TransactionImportEntity>

    fun findByAccountIdAndJobKindOrderByCreatedAtDesc(
        accountId: UUID,
        jobKind: String,
    ): List<TransactionImportEntity>

    fun findByFinancialAccountIdAndAccountIdAndStatusOrderByCreatedAtDesc(
        financialAccountId: UUID,
        accountId: UUID,
        status: ImportJobStatus,
        jobKind: String,
    ): List<TransactionImportEntity>

    fun findByAccountIdAndStatusAndJobKindOrderByCreatedAtDesc(
        accountId: UUID,
        status: ImportJobStatus,
        jobKind: String,
    ): List<TransactionImportEntity>

    @Query(
        """
        UPDATE banking.document_import_jobs
           SET status = :status, updated_at = NOW()
         WHERE id = :id
           AND job_kind = :jobKind
        """,
        nativeQuery = true,
    )
    fun updateStatus(
        id: UUID,
        status: String,
        jobKind: String,
    ): Int

    @Query(
        """
        UPDATE banking.document_import_jobs
           SET assistant_request_published_at = NOW(), updated_at = NOW()
         WHERE id = :id
           AND job_kind = :jobKind
           AND assistant_request_published_at IS NULL
        """,
        nativeQuery = true,
    )
    fun markAssistantRequestPublished(
        id: UUID,
        jobKind: String,
    ): Int
}
