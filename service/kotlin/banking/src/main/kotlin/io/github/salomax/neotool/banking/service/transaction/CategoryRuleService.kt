package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.*
import io.github.salomax.neotool.banking.model.dto.transaction.BulkCategorizeResult
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionRuleInput
import io.github.salomax.neotool.banking.model.dto.transaction.UpdateTransactionRuleInput
import io.github.salomax.neotool.banking.model.domain.transaction.TransactionDescriptionNormalizer
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRuleRepository
import io.github.salomax.neotool.banking.service.transaction.AppliedRuleAuditSupport
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class TransactionRuleService(
    private val ruleRepository: TransactionRuleRepository,
    private val categoryRepository: TransactionCategoryRepository,
    private val merchantRepository: MerchantRepository,
    private val transactionRepository: TransactionRepository,
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

    fun disabledRuleIds(accountId: UUID): Set<UUID> = ruleRepository.findDisabledRuleIdsForAccount(accountId)

    fun setSystemRuleEnabled(
        accountId: UUID,
        ruleId: UUID,
        enabled: Boolean,
    ): Boolean {
        val rule = ruleRepository.findById(ruleId) ?: throw TransactionRuleNotFoundException(ruleId)
        if (!rule.isSystem || rule.accountId != null) {
            throw SystemTransactionRuleNotEditableException()
        }
        return if (enabled) {
            ruleRepository.enableSystemRuleForAccount(accountId, ruleId)
            true
        } else {
            ruleRepository.disableSystemRuleForAccount(accountId, ruleId)
        }
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

    fun countHistoricalMatches(
        ruleId: UUID,
        accountId: UUID,
    ): Int {
        val rule =
            ruleRepository.findById(ruleId)
                ?: throw TransactionRuleNotFoundException(ruleId)
        if (rule.accountId != accountId) {
            throw TransactionRuleNotFoundException(ruleId)
        }
        return transactionRepository.findByAccount(accountId)
            .asSequence()
            .filter { tx -> tx.source != TransactionSource.MANUAL }
            .filter { tx -> tx.type != TransactionType.BALANCE_ADJUSTMENT }
            .count { tx -> matchesRule(tx, rule) }
    }

    fun applyToHistorical(
        ruleId: UUID,
        accountId: UUID,
    ): BulkCategorizeResult {
        val rule =
            ruleRepository.findById(ruleId)
                ?: throw TransactionRuleNotFoundException(ruleId)
        if (rule.accountId != accountId) {
            throw TransactionRuleNotFoundException(ruleId)
        }
        val now = Instant.now()
        var affectedCount = 0

        transactionRepository.findByAccount(accountId)
            .asSequence()
            .filter { tx -> tx.source != TransactionSource.MANUAL }
            .filter { tx -> tx.type != TransactionType.BALANCE_ADJUSTMENT }
            .forEach { tx ->
                if (!matchesRule(tx, rule)) return@forEach

                var next = tx
                var anyApplied = false

                if (rule.categoryId != null && tx.categorySource != CategorySource.MANUAL) {
                    if (tx.categoryId != rule.categoryId || tx.categorySource != CategorySource.RULE) {
                        next = next.copy(categoryId = rule.categoryId, categorySource = CategorySource.RULE)
                        anyApplied = true
                    }
                }
                if (rule.merchantId != null && tx.merchantId != rule.merchantId) {
                    next = next.copy(merchantId = rule.merchantId)
                    anyApplied = true
                }
                if (rule.descriptionOverride != null && tx.description != rule.descriptionOverride) {
                    next = next.copy(description = rule.descriptionOverride)
                    anyApplied = true
                }
                if (rule.tagsOverride != null && tx.tags != rule.tagsOverride) {
                    next = next.copy(tags = rule.tagsOverride)
                    anyApplied = true
                }

                if (!anyApplied) return@forEach

                next =
                    next.copy(
                        appliedRuleHistory =
                            tx.appliedRuleHistory +
                                AppliedRuleAuditSupport.buildEntries(listOf(requireNotNull(rule.id)), now),
                        updatedAt = now,
                    )

                transactionRepository.update(next)
                affectedCount += 1
            }

        if (affectedCount > 0) {
            ruleRepository.incrementMatchCountBy(ruleId, affectedCount)
        }

        return BulkCategorizeResult(affectedCount = affectedCount, errors = emptyList())
    }

    private fun matchesPattern(
        pattern: String,
        patternType: PatternType,
        sample: String,
    ): Boolean =
        when (patternType) {
            PatternType.CONTAINS -> containsWholeWord(sample.lowercase(), pattern.lowercase())
            PatternType.STARTS_WITH -> sample.startsWith(pattern, ignoreCase = true)
            PatternType.EXACT -> sample.equals(pattern, ignoreCase = true)
            PatternType.REGEX -> Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(sample)
        }

    private fun matchesRule(
        tx: Transaction,
        rule: TransactionRule,
    ): Boolean {
        val side =
            if (tx.type == TransactionType.TRANSFER) {
                CategoryApplicableTo.TRANSFER
            } else if (tx.creditDebitType == CreditDebitType.CREDIT) {
                CategoryApplicableTo.INCOME
            } else {
                CategoryApplicableTo.EXPENSE
            }
        if (rule.applicableTo != CategoryApplicableTo.ANY && rule.applicableTo != side) return false

        val normalized = TransactionDescriptionNormalizer.normalize(tx.description)
        val pattern = rule.pattern

        return when (rule.patternType) {
            PatternType.CONTAINS -> containsWholeWord(normalized, pattern.lowercase())
            PatternType.STARTS_WITH -> normalized.startsWith(pattern)
            PatternType.EXACT -> normalized == pattern
            PatternType.REGEX ->
                try {
                    normalized.matches(Regex(pattern, RegexOption.IGNORE_CASE))
                } catch (_: Exception) {
                    false
                }
        }
    }

    /**
     * Checks if [text] contains [word] as a whole word — not as part of another word.
     * Both [text] and [word] must be pre-lowercased.
     *
     * Examples:
     *   "clauber" contains "uber"  → false  (letter before 'u')
     *   "uber eats" contains "uber" → true
     *   "uber*trip" contains "uber" → true   ('*' is not a letter)
     *   "uber99" contains "uber"   → true    ('9' is not a letter)
     */
    private fun containsWholeWord(text: String, word: String): Boolean =
        Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])").containsMatchIn(text)

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
