package io.github.salomax.neotool.banking.domain.transaction

import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class CategoryRuleService(
    private val ruleRepository: CategoryRuleRepository,
    private val categoryRepository: TransactionCategoryRepository,
) {
    /** Returns all user-owned rules (explicit + implicit) for the account. System rules are excluded. */
    fun listUserRules(accountId: UUID): List<CategoryRule> = ruleRepository.findUserRulesForAccount(accountId)

    fun create(
        input: CreateCategoryRuleInput,
        accountId: UUID,
    ): CategoryRule {
        requireCategoryAccessible(input.categoryId, accountId)
        validatePattern(input.patternType, input.pattern)

        val duplicate =
            ruleRepository.findUserRulesForAccount(accountId)
                .any { it.pattern == input.pattern && it.patternType == input.patternType }
        if (duplicate) {
            throw DuplicateCategoryRuleException(input.pattern, input.patternType)
        }

        val now = Instant.now()
        val rule =
            CategoryRule(
                accountId = accountId,
                categoryId = input.categoryId,
                pattern = input.pattern.trim(),
                patternType = input.patternType,
                applicableTo = input.applicableTo,
                priority = input.priority ?: 40,
                isSystem = false,
                isImplicit = false,
                matchCount = 0,
                createdAt = now,
                updatedAt = now,
            )
        return ruleRepository.save(rule)
    }

    fun update(
        id: UUID,
        input: UpdateCategoryRuleInput,
        accountId: UUID,
    ): CategoryRule {
        val existing =
            ruleRepository.findById(id)
                ?: throw CategoryRuleNotFoundException(id)
        if (existing.accountId != accountId) {
            throw CategoryRuleNotFoundException(id)
        }
        if (existing.isSystem) {
            throw SystemCategoryRuleNotEditableException()
        }

        input.patternType?.let { validatePattern(it, input.pattern ?: existing.pattern) }

        val pattern = (input.pattern?.trim() ?: existing.pattern)
        val patternType = input.patternType ?: existing.patternType
        if (pattern != existing.pattern || patternType != existing.patternType) {
            val duplicate =
                ruleRepository.findUserRulesForAccount(accountId)
                    .any { it.id != id && it.pattern == pattern && it.patternType == patternType }
            if (duplicate) {
                throw DuplicateCategoryRuleException(pattern, patternType)
            }
        }

        input.categoryId?.let { requireCategoryAccessible(it, accountId) }

        val now = Instant.now()
        val updated =
            existing.copy(
                categoryId = input.categoryId ?: existing.categoryId,
                pattern = pattern,
                patternType = patternType,
                applicableTo = existing.applicableTo,
                priority = input.priority ?: existing.priority,
                updatedAt = now,
            )
        return ruleRepository.save(updated)
    }

    fun delete(
        id: UUID,
        accountId: UUID,
    ): Boolean {
        val rule =
            ruleRepository.findById(id)
                ?: throw CategoryRuleNotFoundException(id)
        if (rule.accountId != accountId) {
            throw CategoryRuleNotFoundException(id)
        }
        if (rule.isSystem) {
            throw SystemCategoryRuleNotDeletableException()
        }
        ruleRepository.softDelete(id, accountId)
        return true
    }

    private fun requireCategoryAccessible(
        categoryId: UUID,
        accountId: UUID,
    ) {
        val category = categoryRepository.findById(categoryId) ?: throw CategoryNotFoundException(categoryId)
        val accessible = category.accountId == null || category.accountId == accountId
        require(accessible) { throw CategoryNotFoundException(categoryId) }
    }

    private fun validatePattern(
        patternType: PatternType,
        pattern: String,
    ) {
        if (patternType != PatternType.REGEX) return
        try {
            Regex(pattern)
        } catch (e: Exception) {
            throw InvalidRegexPatternException(pattern, e)
        }
    }
}

data class CreateCategoryRuleInput(
    val categoryId: UUID,
    val pattern: String,
    val patternType: PatternType,
    val applicableTo: CategoryApplicableTo,
    val priority: Int? = null,
)

data class UpdateCategoryRuleInput(
    val categoryId: UUID? = null,
    val pattern: String? = null,
    val patternType: PatternType? = null,
    val priority: Int? = null,
)
