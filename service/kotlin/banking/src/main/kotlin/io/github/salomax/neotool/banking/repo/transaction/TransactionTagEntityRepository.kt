package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.TransactionTagEntity
import io.github.salomax.neotool.banking.model.transaction.TransactionTagId
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface TransactionTagEntityRepository : JpaRepository<TransactionTagEntity, TransactionTagId> {
    fun findByTransactionIdInAndAccountId(
        transactionIds: List<UUID>,
        accountId: UUID,
    ): List<TransactionTagEntity>

    fun deleteByTransactionIdAndAccountId(
        transactionId: UUID,
        accountId: UUID,
    ): Long

    @Query(
        """
        SELECT DISTINCT tt.tag
          FROM banking.transaction_tags tt
          JOIN banking.transactions t
            ON t.id = tt.transaction_id
         WHERE tt.account_id = :accountId
           AND t.deleted_at IS NULL
         ORDER BY tt.tag
        """,
        nativeQuery = true,
    )
    fun findDistinctTagsByAccountId(accountId: UUID): List<String>
}
