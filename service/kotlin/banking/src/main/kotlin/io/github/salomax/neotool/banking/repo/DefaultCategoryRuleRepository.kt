package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.transaction.CategoryRule
import io.github.salomax.neotool.banking.domain.transaction.CategoryRuleRepository
import io.github.salomax.neotool.banking.model.transaction.CategoryRuleMapper
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class DefaultCategoryRuleRepository(
    private val entityRepository: CategoryRuleEntityRepository,
) : CategoryRuleRepository {
    override fun findRulesForAccount(accountId: UUID): List<CategoryRule> =
        entityRepository.findRulesForAccount(accountId).map(CategoryRuleMapper::toDomain)

    override fun findUserRulesForAccount(accountId: UUID): List<CategoryRule> =
        entityRepository.findUserRulesForAccount(accountId).map(CategoryRuleMapper::toDomain)

    override fun findById(id: UUID): CategoryRule? =
        entityRepository.findByIdAndDeletedAtIsNull(id)?.let(CategoryRuleMapper::toDomain)

    override fun save(rule: CategoryRule): CategoryRule {
        val entity =
            if (rule.id == null) {
                CategoryRuleMapper.toEntity(rule)
            } else {
                entityRepository.findByIdAndDeletedAtIsNull(rule.id)
                    ?.apply {
                        accountId = rule.accountId
                        categoryId = rule.categoryId
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
                    ?: CategoryRuleMapper.toEntity(rule)
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
    ): CategoryRule? =
        entityRepository.findByAccountIdAndPatternAndIsImplicitAndDeletedAtIsNull(
            accountId = accountId,
            pattern = pattern,
            isImplicit = true,
        )?.let(CategoryRuleMapper::toDomain)

    override fun incrementMatchCount(id: UUID) {
        entityRepository.incrementMatchCount(id, Instant.now())
    }
}
