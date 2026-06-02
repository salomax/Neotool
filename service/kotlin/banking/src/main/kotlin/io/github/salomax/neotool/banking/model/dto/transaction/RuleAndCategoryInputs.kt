package io.github.salomax.neotool.banking.model.dto.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import java.util.UUID

data class CreateTransactionRuleInput(
    val categoryId: UUID? = null,
    val merchantId: UUID? = null,
    val descriptionOverride: String? = null,
    val tagsOverride: List<String>? = null,
    val pattern: String,
    val patternType: PatternType,
    val applicableTo: CategoryApplicableTo,
    val priority: Int? = null,
)

data class UpdateTransactionRuleInput(
    val categoryId: UUID? = null,
    val merchantId: UUID? = null,
    /** When true, merchantId is being explicitly set (possibly to null to clear it). */
    val merchantIdProvided: Boolean = false,
    val descriptionOverride: String? = null,
    /** When true, descriptionOverride is being explicitly set (possibly to null to clear it). */
    val descriptionOverrideProvided: Boolean = false,
    val tagsOverride: List<String>? = null,
    /** When true, tagsOverride is being explicitly set (possibly to null to clear it). */
    val tagsOverrideProvided: Boolean = false,
    val applicableTo: CategoryApplicableTo? = null,
    val pattern: String? = null,
    val patternType: PatternType? = null,
    val priority: Int? = null,
)

data class CreateCategoryInput(
    val parentId: UUID?,
    val name: String,
    val applicableTo: CategoryApplicableTo,
    val icon: String?,
    val color: String?,
    val sortOrder: Int?,
)

data class UpdateCategoryInput(
    val name: String?,
    val icon: String?,
    val color: String?,
    val sortOrder: Int?,
)
