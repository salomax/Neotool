package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import io.github.salomax.neotool.banking.repo.transaction.TransactionRuleRepository
import io.github.salomax.neotool.banking.model.transaction.CategoryRuleMapper
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class DefaultCategoryRuleRepository(
    private val entityRepository: CategoryRuleEntityRepository,
) : TransactionRuleRepository {
    override fun findRulesForAccount(accountId: UUID): List<TransactionRule> =
        entityRepository.findRulesForAccount(accountId).map(CategoryRuleMapper::toDomain)

    override fun findUserRulesForAccount(accountId: UUID): List<TransactionRule> =
        entityRepository.findUserRulesForAccount(accountId).map(CategoryRuleMapper::toDomain)

    override fun findById(id: UUID): TransactionRule? =
        entityRepository.findByIdAndDeletedAtIsNull(id)?.let(CategoryRuleMapper::toDomain)

    override fun save(rule: TransactionRule): TransactionRule {
        val mappedRule = CategoryRuleMapper.toEntity(rule)
        val entity =
            if (rule.id == null) {
                mappedRule
            } else {
                entityRepository.findByIdAndDeletedAtIsNull(rule.id)
                    ?.apply {
                        accountId = rule.accountId
                        categoryId = rule.categoryId
                        merchantId = rule.merchantId
                        descriptionOverride = rule.descriptionOverride
                        tagsOverrideJson = mappedRule.tagsOverrideJson
                        pattern = rule.pattern
                        patternType = rule.patternType
                        applicableTo = rule.applicableTo
                        priority = rule.priority
                        isSystem = rule.isSystem
                        isImplicit = rule.isImplicit
                        matchCount = rule.matchCount
                        deletedAt = rule.deletedAt
                        createdAt = rule.createdAt
                        updatedAt = rule.updatedAt
                    }
                    ?: mappedRule
            }
        return CategoryRuleMapper.toDomain(entityRepository.save(entity))
    }

    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ) {
        entityRepository.softDelete(id, accountId, Instant.now())
    }

    override fun findImplicitByPattern(
        accountId: UUID,
        pattern: String,
    ): TransactionRule? =
        entityRepository.findByAccountIdAndPatternAndIsImplicitAndDeletedAtIsNull(
            accountId = accountId,
            pattern = pattern,
            isImplicit = true,
        )?.let(CategoryRuleMapper::toDomain)

    override fun incrementMatchCount(id: UUID) {
        entityRepository.incrementMatchCount(id, Instant.now())
    }
}
