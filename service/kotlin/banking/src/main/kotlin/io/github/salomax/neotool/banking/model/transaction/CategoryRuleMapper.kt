package io.github.salomax.neotool.banking.model.transaction

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule

/**
 * Maps between [TransactionRule] domain and [CategoryRuleEntity].
 */
object CategoryRuleMapper {
    private val objectMapper = ObjectMapper()

    fun toDomain(entity: CategoryRuleEntity): TransactionRule =
        TransactionRule(
            id = entity.id,
            accountId = entity.accountId,
            categoryId = entity.categoryId,
            merchantId = entity.merchantId,
            descriptionOverride = entity.descriptionOverride,
            tagsOverride = entity.tagsOverrideJson?.let(::deserializeTags),
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

    fun toEntity(domain: TransactionRule): CategoryRuleEntity =
        CategoryRuleEntity(
            id = domain.id,
            accountId = domain.accountId,
            categoryId = domain.categoryId,
            merchantId = domain.merchantId,
            descriptionOverride = domain.descriptionOverride,
            tagsOverrideJson = domain.tagsOverride?.let(::serializeTags),
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

    private fun serializeTags(tags: List<String>): String = objectMapper.writeValueAsString(tags)

    private fun deserializeTags(json: String): List<String> =
        objectMapper.readValue(json, object : TypeReference<List<String>>() {})
}
