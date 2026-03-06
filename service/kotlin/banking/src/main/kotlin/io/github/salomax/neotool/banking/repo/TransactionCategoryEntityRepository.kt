package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.model.transaction.TransactionCategoryEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for banking.transaction_categories (entity layer).
 */
@Repository
interface TransactionCategoryEntityRepository : JpaRepository<TransactionCategoryEntity, UUID> {
    /**
     * Returns all system categories + user-defined categories for the given account.
     * Excludes soft-deleted rows.
     */
    fun findByAccountIdIsNullOrAccountIdAndDeletedAtIsNull(accountId: UUID): List<TransactionCategoryEntity>

    fun findByIdAndDeletedAtIsNull(id: UUID): TransactionCategoryEntity?
}
