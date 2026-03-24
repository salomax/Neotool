package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.*
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class TransactionRuleService(
    private val ruleRepository: TransactionRuleRepository,
    private val categoryRepository: TransactionCategoryRepository,
    private val merchantRepository: MerchantRepository,
) {
    /** Returns all user-owned rules (explicit + implicit) for the account. System rules are excluded. */
    fun listUserRules(accountId: UUID): List<TransactionRule> = ruleRepository.findUserRulesForAccount(accountId)

    fun create(
        input: CreateTransactionRuleInput,
        accountId: UUID,
    ): TransactionRule {
        require(input.categoryId != null || input.merchantId != null) {
            "At least one of categoryId or merchantId is required"
        }
        input.categoryId?.let { requireCategoryAccessible(it, accountId) }
        input.merchantId?.let { requireMerchantAccessible(it, accountId) }
        validatePattern(input.patternType, input.pattern)

        val duplicate =
            ruleRepository.findUserRulesForAccount(accountId)
                .any { it.pattern == input.pattern && it.patternType == input.patternType }
        if (duplicate) {
            throw DuplicateTransactionRuleException(input.pattern, input.patternType)
        }

        val now = Instant.now()
        val rule =
            TransactionRule(
                accountId = accountId,
                categoryId = input.categoryId,
                merchantId = input.merchantId,
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
        input: UpdateTransactionRuleInput,
        accountId: UUID,
    ): TransactionRule {
        val existing =
            ruleRepository.findById(id)
                ?: throw TransactionRuleNotFoundException(id)
        if (existing.accountId != accountId) {
            throw TransactionRuleNotFoundException(id)
        }
        if (existing.isSystem) {
            throw SystemTransactionRuleNotEditableException()
        }

        input.patternType?.let { validatePattern(it, input.pattern ?: existing.pattern) }

        val pattern = (input.pattern?.trim() ?: existing.pattern)
        val patternType = input.patternType ?: existing.patternType
        if (pattern != existing.pattern || patternType != existing.patternType) {
            val duplicate =
                ruleRepository.findUserRulesForAccount(accountId)
                    .any { it.id != id && it.pattern == pattern && it.patternType == patternType }
            if (duplicate) {
                throw DuplicateTransactionRuleException(pattern, patternType)
            }
        }

        input.categoryId?.let { requireCategoryAccessible(it, accountId) }
        input.merchantId?.let { requireMerchantAccessible(it, accountId) }

        val newCategoryId = input.categoryId ?: existing.categoryId
        val newMerchantId = if (input.merchantIdProvided) input.merchantId else existing.merchantId
        require(newCategoryId != null || newMerchantId != null) {
            "At least one of categoryId or merchantId is required"
        }

        val now = Instant.now()
        val updated =
            existing.copy(
                categoryId = newCategoryId,
                merchantId = newMerchantId,
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
                ?: throw TransactionRuleNotFoundException(id)
        if (rule.accountId != accountId) {
            throw TransactionRuleNotFoundException(id)
        }
        if (rule.isSystem) {
            throw SystemTransactionRuleNotDeletableException()
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

    private fun requireMerchantAccessible(
        merchantId: UUID,
        accountId: UUID,
    ) {
        val merchant = merchantRepository.findById(merchantId) ?: throw MerchantNotFoundException(merchantId)
        val accessible = merchant.accountId == null || merchant.accountId == accountId
        require(accessible) { throw MerchantNotFoundException(merchantId) }
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

data class CreateTransactionRuleInput(
    val categoryId: UUID? = null,
    val merchantId: UUID? = null,
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
    val pattern: String? = null,
    val patternType: PatternType? = null,
    val priority: Int? = null,
)
