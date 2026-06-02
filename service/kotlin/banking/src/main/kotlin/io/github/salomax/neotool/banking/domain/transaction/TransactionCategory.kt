package io.github.salomax.neotool.banking.domain.transaction

import java.time.Instant
import java.util.UUID

/**
 * Domain model for banking.transaction_categories.
 * Two-level hierarchy: root categories (parentId == null) + subcategories.
 * System categories (isSystem = true, accountId = null) are seeded and read-only.
 * User-defined categories have accountId set and can be soft-deleted.
 */
data class TransactionCategory(
    val id: UUID? = null,
    /** Null = system/global category. Set = user-defined (scoped to this account). */
    val accountId: UUID? = null,
    val parentId: UUID? = null,
    val name: String,
    val applicableTo: CategoryApplicableTo,
    val icon: String? = null,
    val color: String? = null,
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
