package io.github.salomax.neotool.banking.domain.transaction

import java.time.Instant
import java.util.UUID

/**
 * Domain model for banking.category_rules.
 * Maps a text pattern to a category for auto-categorization.
 */
data class CategoryRule(
    val id: UUID? = null,
    val accountId: UUID? = null,
    val categoryId: UUID,
    val pattern: String,
    val patternType: PatternType,
    val applicableTo: CategoryApplicableTo,
    val priority: Int = 100,
    val isSystem: Boolean = false,
    val isImplicit: Boolean = false,
    val matchCount: Int = 0,
    val deletedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
