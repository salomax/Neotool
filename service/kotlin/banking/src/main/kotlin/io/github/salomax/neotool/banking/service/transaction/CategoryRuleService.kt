package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.*
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRuleRepository
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

    /** Returns rules filtered by system/user ownership. */
    fun listRules(
        accountId: UUID,
        isSystem: Boolean?,
    ): List<TransactionRule> =
        when (isSystem) {
            true -> ruleRepository.findSystemRules()
            false -> ruleRepository.findUserRulesForAccount(accountId)
            null -> ruleRepository.findRulesForAccount(accountId)
        }

    fun create(
        input: CreateTransactionRuleInput,
        accountId: UUID,
    ): TransactionRule {
        require(
            input.categoryId != null || input.merchantId != null ||
                input.descriptionOverride != null || input.tagsOverride != null,
        ) { "At least one of categoryId, merchantId, descriptionOverride, or tagsOverride is required" }
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
                descriptionOverride = input.descriptionOverride?.trim(),
                tagsOverride = input.tagsOverride,
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
        val newDescriptionOverride =
            if (input.descriptionOverrideProvided) input.descriptionOverride else existing.descriptionOverride
        val newTagsOverride =
            if (input.tagsOverrideProvided) input.tagsOverride else existing.tagsOverride
        require(
            newCategoryId != null || newMerchantId != null ||
                newDescriptionOverride != null || newTagsOverride != null,
        ) { "At least one of categoryId, merchantId, descriptionOverride, or tagsOverride is required" }

        val now = Instant.now()
        val updated =
            existing.copy(
                categoryId = newCategoryId,
                merchantId = newMerchantId,
                descriptionOverride = newDescriptionOverride?.trim(),
                tagsOverride = newTagsOverride,
                pattern = pattern,
                patternType = patternType,
                applicableTo = input.applicableTo ?: existing.applicableTo,
                priority = input.priority ?: existing.priority,
                isImplicit = if (existing.isImplicit && hasExplicitChanges(input)) false else existing.isImplicit,
                updatedAt = now,
            )
        return ruleRepository.save(updated)
    }

    /** Returns true when the update input contains explicit user-driven changes that should promote an implicit rule. */
    private fun hasExplicitChanges(input: UpdateTransactionRuleInput): Boolean =
        input.categoryId != null || input.merchantIdProvided || input.descriptionOverrideProvided ||
            input.tagsOverrideProvided || input.pattern != null || input.patternType != null ||
            input.applicableTo != null

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

    fun previewPattern(
        pattern: String,
        patternType: PatternType,
        sample: String,
    ): Boolean {
        validatePattern(patternType, pattern)
        return matchesPattern(pattern.trim(), patternType, sample)
    }

    fun reorder(
        ids: List<UUID>,
        accountId: UUID,
    ): List<TransactionRule> {
        require(ids.isNotEmpty()) { "ids must not be empty" }
        val existing = ruleRepository.findUserRulesForAccount(accountId).associateBy { it.id!! }
        for (id in ids) {
            requireNotNull(existing[id]) { "Rule $id not found or not owned by account" }
        }
        val now = Instant.now()
        ids.forEachIndexed { index, id ->
            ruleRepository.updatePriority(id, index + 1, now)
        }
        return ruleRepository.findUserRulesForAccount(accountId)
    }

    private fun matchesPattern(
        pattern: String,
        patternType: PatternType,
        sample: String,
    ): Boolean =
        when (patternType) {
            PatternType.CONTAINS -> sample.contains(pattern, ignoreCase = true)
            PatternType.STARTS_WITH -> sample.startsWith(pattern, ignoreCase = true)
            PatternType.EXACT -> sample.equals(pattern, ignoreCase = true)
            PatternType.REGEX -> Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(sample)
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
