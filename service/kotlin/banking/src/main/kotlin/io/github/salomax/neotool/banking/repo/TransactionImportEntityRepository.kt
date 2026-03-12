package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.model.importjob.TransactionImportEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for banking.transaction_imports.
 * All finders scope by account_id for tenant isolation.
 */
@Repository
interface TransactionImportEntityRepository : JpaRepository<TransactionImportEntity, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): TransactionImportEntity?

    fun findByFinancialAccountIdAndFileHash(
        financialAccountId: UUID,
        fileHash: String,
    ): TransactionImportEntity?

    @Query(
        """
        SELECT *
          FROM banking.transaction_imports
         WHERE account_id = :accountId
           AND file_hash = :fileHash
         ORDER BY created_at DESC
         LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findLatestByAccountIdAndFileHash(
        accountId: UUID,
        fileHash: String,
    ): TransactionImportEntity?

    fun findByFinancialAccountIdAndAccountIdOrderByCreatedAtDesc(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<TransactionImportEntity>

    fun findByAccountIdOrderByCreatedAtDesc(accountId: UUID): List<TransactionImportEntity>

    @Query(
        """
        UPDATE banking.transaction_imports
           SET status = :status, updated_at = NOW()
         WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun updateStatus(
        id: UUID,
        status: String,
    ): Int

    @Query(
        """
        UPDATE banking.transaction_imports
           SET assistant_request_published_at = NOW(), updated_at = NOW()
         WHERE id = :id
           AND assistant_request_published_at IS NULL
        """,
        nativeQuery = true,
    )
    fun markAssistantRequestPublished(id: UUID): Int
}
