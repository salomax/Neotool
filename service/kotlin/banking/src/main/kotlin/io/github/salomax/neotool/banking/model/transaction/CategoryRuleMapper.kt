package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryRule

/**
 * Maps between [CategoryRule] domain and [CategoryRuleEntity].
 */
object CategoryRuleMapper {
    fun toDomain(entity: CategoryRuleEntity): CategoryRule =
        CategoryRule(
            id = entity.id,
            accountId = entity.accountId,
            categoryId = entity.categoryId,
            pattern = entity.pattern,
            patternType = entity.patternType,
            applicableTo = entity.applicableTo,
            priority = entity.priority,
            isSystem = entity.isSystem,
            isImplicit = entity.isImplicit,
            matchCount = entity.matchCount,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: CategoryRule): CategoryRuleEntity =
        CategoryRuleEntity(
            id = domain.id,
            accountId = domain.accountId,
            categoryId = domain.categoryId,
            pattern = domain.pattern,
            patternType = domain.patternType,
            applicableTo = domain.applicableTo,
            priority = domain.priority,
            isSystem = domain.isSystem,
            isImplicit = domain.isImplicit,
            matchCount = domain.matchCount,
            deletedAt = domain.deletedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
}
