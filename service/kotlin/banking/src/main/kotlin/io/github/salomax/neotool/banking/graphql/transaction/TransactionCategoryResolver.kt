package io.github.salomax.neotool.banking.graphql.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.service.transaction.CreateCategoryInput
import io.github.salomax.neotool.banking.service.transaction.CreateTransactionRuleInput
import io.github.salomax.neotool.banking.domain.transaction.Merchant
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.transaction.TransactionCategoryService
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleService
import io.github.salomax.neotool.banking.service.transaction.UpdateCategoryInput
import io.github.salomax.neotool.banking.service.transaction.UpdateTransactionRuleInput
import io.github.salomax.neotool.banking.graphql.merchant.MerchantDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionRuleDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class TransactionCategoryResolver(
    private val service: TransactionCategoryService,
    private val ruleService: TransactionRuleService,
    private val categoryRepository: TransactionCategoryRepository,
    private val merchantRepository: MerchantRepository,
) {
    fun createTransactionCategory(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionCategoryDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }

        val parentId = (input["parentId"] as? String)?.let { UUID.fromString(it) }
        val name = input["name"] as? String ?: throw IllegalArgumentException("name is required")
        val applicableToRaw =
            input["applicableTo"] as? String
                ?: throw IllegalArgumentException("applicableTo is required")
        val applicableTo = CategoryApplicableTo.valueOf(applicableToRaw)
        val icon = input["icon"] as? String
        val color = input["color"] as? String
        val sortOrder = (input["sortOrder"] as? Number)?.toInt()

        val created =
            service.create(
                input =
                    CreateCategoryInput(
                        parentId = parentId,
                        name = name,
                        applicableTo = applicableTo,
                        icon = icon,
                        color = color,
                        sortOrder = sortOrder,
                    ),
                accountId = accountId,
            )

        return TransactionCategoryDTO(
            id = created.id!!,
            parentId = created.parentId,
            name = created.name,
            applicableTo = created.applicableTo,
            icon = created.icon,
            color = created.color,
            isSystem = created.isSystem,
            sortOrder = created.sortOrder,
        )
    }

    fun updateTransactionCategory(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionCategoryDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val categoryId = UUID.fromString(id)

        val updateInput =
            UpdateCategoryInput(
                name = input["name"] as? String,
                icon = input["icon"] as? String,
                color = input["color"] as? String,
                sortOrder = (input["sortOrder"] as? Number)?.toInt(),
            )

        val updated = service.update(categoryId, updateInput, accountId)

        return TransactionCategoryDTO(
            id = updated.id!!,
            parentId = updated.parentId,
            name = updated.name,
            applicableTo = updated.applicableTo,
            icon = updated.icon,
            color = updated.color,
            isSystem = updated.isSystem,
            sortOrder = updated.sortOrder,
        )
    }

    fun deleteTransactionCategory(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val categoryId = UUID.fromString(id)
        return service.delete(categoryId, accountId)
    }

    // ─── Transaction rules ────────────────────────────────────────────────────

    fun transactionRules(
        isSystem: Boolean?,
        principal: RequestPrincipal,
    ): List<TransactionRuleDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val rules = ruleService.listRules(accountId, isSystem)
        val categoryIds = rules.mapNotNull { it.categoryId }.toSet()
        val merchantIds = rules.mapNotNull { it.merchantId }.toSet()
        val categories = categoryIds.associateWith { categoryRepository.findById(it) }
        val merchants = merchantIds.associateWith { merchantRepository.findById(it) }
        return rules.mapIndexed { index, rule ->
            rule.toDTO(
                category = categories[rule.categoryId],
                merchant = merchants[rule.merchantId],
                position = index + 1,
            )
        }
    }

    fun createTransactionRule(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionRuleDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val categoryId = (input["categoryId"] as? String)?.let { UUID.fromString(it) }
        val merchantId = (input["merchantId"] as? String)?.let { UUID.fromString(it) }
        val pattern = input["pattern"] as? String ?: throw IllegalArgumentException("pattern is required")
        val patternTypeRaw =
            input["patternType"] as? String ?: throw IllegalArgumentException("patternType is required")
        val patternType = PatternType.valueOf(patternTypeRaw)
        val applicableToRaw =
            input["applicableTo"] as? String ?: throw IllegalArgumentException("applicableTo is required")
        val applicableTo = CategoryApplicableTo.valueOf(applicableToRaw)
        val priority = (input["priority"] as? Number)?.toInt()

        val descriptionOverride = input["descriptionOverride"] as? String
        val tagsOverride = (input["tagsOverride"] as? List<*>)?.filterIsInstance<String>()

        val created =
            ruleService.create(
                CreateTransactionRuleInput(
                    categoryId = categoryId,
                    merchantId = merchantId,
                    descriptionOverride = descriptionOverride,
                    tagsOverride = tagsOverride,
                    pattern = pattern,
                    patternType = patternType,
                    applicableTo = applicableTo,
                    priority = priority,
                ),
                accountId,
            )
        val category = created.categoryId?.let { categoryRepository.findById(it) }
        val merchant = created.merchantId?.let { merchantRepository.findById(it) }
        return created.toDTO(category, merchant, position = 1)
    }

    fun updateTransactionRule(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionRuleDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val ruleId = UUID.fromString(id)
        val merchantIdProvided = input.containsKey("merchantId")
        val descriptionOverrideProvided = input.containsKey("descriptionOverride")
        val tagsOverrideProvided = input.containsKey("tagsOverride")
        val updateInput =
            UpdateTransactionRuleInput(
                categoryId = (input["categoryId"] as? String)?.let { UUID.fromString(it) },
                merchantId = (input["merchantId"] as? String)?.let { UUID.fromString(it) },
                merchantIdProvided = merchantIdProvided,
                descriptionOverride = input["descriptionOverride"] as? String,
                descriptionOverrideProvided = descriptionOverrideProvided,
                tagsOverride = (input["tagsOverride"] as? List<*>)?.filterIsInstance<String>(),
                tagsOverrideProvided = tagsOverrideProvided,
                applicableTo = (input["applicableTo"] as? String)?.let { CategoryApplicableTo.valueOf(it) },
                pattern = input["pattern"] as? String,
                patternType = (input["patternType"] as? String)?.let { PatternType.valueOf(it) },
                priority = (input["priority"] as? Number)?.toInt(),
            )
        val updated = ruleService.update(ruleId, updateInput, accountId)
        val category = updated.categoryId?.let { categoryRepository.findById(it) }
        val merchant = updated.merchantId?.let { merchantRepository.findById(it) }
        return updated.toDTO(category, merchant, position = 1)
    }

    fun deleteTransactionRule(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val ruleId = UUID.fromString(id)
        return ruleService.delete(ruleId, accountId)
    }

    fun previewTransactionRule(
        pattern: String,
        patternType: String,
        sample: String,
    ): Boolean = ruleService.previewPattern(pattern, PatternType.valueOf(patternType), sample)

    fun reorderTransactionRules(
        ids: List<String>,
        principal: RequestPrincipal,
    ): List<TransactionRuleDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val uuids = ids.map { UUID.fromString(it) }
        val rules = ruleService.reorder(uuids, accountId)
        val categoryIds = rules.mapNotNull { it.categoryId }.toSet()
        val merchantIds = rules.mapNotNull { it.merchantId }.toSet()
        val categories = categoryIds.associateWith { categoryRepository.findById(it) }
        val merchants = merchantIds.associateWith { merchantRepository.findById(it) }
        return rules.mapIndexed { index, rule ->
            rule.toDTO(
                category = categories[rule.categoryId],
                merchant = merchants[rule.merchantId],
                position = index + 1,
            )
        }
    }

    private fun TransactionRule.toDTO(
        category: TransactionCategory?,
        merchant: Merchant?,
        position: Int,
    ): TransactionRuleDTO =
        TransactionRuleDTO(
            id = id!!,
            categoryId = categoryId,
            category = category?.toDTO(),
            merchantId = merchantId,
            merchant = merchant?.toMerchantDTO(),
            descriptionOverride = descriptionOverride,
            tagsOverride = tagsOverride,
            pattern = pattern,
            patternType = patternType,
            applicableTo = applicableTo,
            priority = priority,
            isSystem = isSystem,
            isImplicit = isImplicit,
            matchCount = matchCount,
            position = position,
            createdAt = createdAt,
        )

    private fun TransactionCategory.toDTO(): TransactionCategoryDTO =
        TransactionCategoryDTO(
            id = id!!,
            parentId = parentId,
            name = name,
            applicableTo = applicableTo,
            icon = icon,
            color = color,
            isSystem = isSystem,
            sortOrder = sortOrder,
        )

    private fun Merchant.toMerchantDTO(): MerchantDTO {
        val logoAssetId = domain?.let { merchantRepository.findLogoAssetIdByDomain(it) }
        return MerchantDTO(
            id = id!!,
            name = name,
            domain = domain,
            sector = sector,
            isSystem = isSystem,
            logoAsset = logoAssetId?.let { mapOf("__typename" to "Asset", "id" to it) },
        )
    }
}
