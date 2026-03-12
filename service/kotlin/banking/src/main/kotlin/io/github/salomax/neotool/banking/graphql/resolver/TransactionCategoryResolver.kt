package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryRule
import io.github.salomax.neotool.banking.domain.transaction.CategoryRuleService
import io.github.salomax.neotool.banking.domain.transaction.CreateCategoryInput
import io.github.salomax.neotool.banking.domain.transaction.CreateCategoryRuleInput
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategoryService
import io.github.salomax.neotool.banking.domain.transaction.UpdateCategoryInput
import io.github.salomax.neotool.banking.domain.transaction.UpdateCategoryRuleInput
import io.github.salomax.neotool.banking.graphql.dto.CategoryRuleDTO
import io.github.salomax.neotool.banking.graphql.dto.TransactionCategoryDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class TransactionCategoryResolver(
    private val service: TransactionCategoryService,
    private val ruleService: CategoryRuleService,
    private val categoryRepository: TransactionCategoryRepository,
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

    // ─── Category rules (CAT-13) ─────────────────────────────────────────────

    fun categoryRules(principal: RequestPrincipal): List<CategoryRuleDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val rules = ruleService.listUserRules(accountId)
        val categoryIds = rules.map { it.categoryId }.toSet()
        val categories = categoryIds.associateWith { categoryRepository.findById(it)!! }
        return rules.map { rule -> rule.toDTO(categories[rule.categoryId]!!) }
    }

    fun createCategoryRule(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): CategoryRuleDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val categoryId = UUID.fromString(input["categoryId"] as String)
        val pattern = input["pattern"] as? String ?: throw IllegalArgumentException("pattern is required")
        val patternTypeRaw =
            input["patternType"] as? String ?: throw IllegalArgumentException(
                "patternType is required",
            )
        val patternType = io.github.salomax.neotool.banking.domain.transaction.PatternType.valueOf(patternTypeRaw)
        val applicableToRaw =
            input["applicableTo"] as? String ?: throw IllegalArgumentException(
                "applicableTo is required",
            )
        val applicableTo = CategoryApplicableTo.valueOf(applicableToRaw)
        val priority = (input["priority"] as? Number)?.toInt()

        val created =
            ruleService.create(
                CreateCategoryRuleInput(
                    categoryId = categoryId,
                    pattern = pattern,
                    patternType = patternType,
                    applicableTo = applicableTo,
                    priority = priority,
                ),
                accountId,
            )
        val category = categoryRepository.findById(created.categoryId)!!
        return created.toDTO(category)
    }

    fun updateCategoryRule(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): CategoryRuleDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val ruleId = UUID.fromString(id)
        val updateInput =
            UpdateCategoryRuleInput(
                categoryId = (input["categoryId"] as? String)?.let { UUID.fromString(it) },
                pattern = input["pattern"] as? String,
                patternType =
                    (input["patternType"] as? String)?.let {
                        io.github.salomax.neotool.banking.domain.transaction.PatternType.valueOf(it)
                    },
                priority = (input["priority"] as? Number)?.toInt(),
            )
        val updated = ruleService.update(ruleId, updateInput, accountId)
        val category = categoryRepository.findById(updated.categoryId)!!
        return updated.toDTO(category)
    }

    fun deleteCategoryRule(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val ruleId = UUID.fromString(id)
        return ruleService.delete(ruleId, accountId)
    }

    private fun CategoryRule.toDTO(category: TransactionCategory): CategoryRuleDTO =
        CategoryRuleDTO(
            id = id!!,
            categoryId = categoryId,
            category = category.toDTO(),
            pattern = pattern,
            patternType = patternType,
            applicableTo = applicableTo,
            priority = priority,
            isSystem = isSystem,
            isImplicit = isImplicit,
            matchCount = matchCount,
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
}
