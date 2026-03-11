package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CreateCategoryInput
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategoryService
import io.github.salomax.neotool.banking.domain.transaction.UpdateCategoryInput
import io.github.salomax.neotool.banking.graphql.dto.TransactionCategoryDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class TransactionCategoryResolver(
    private val service: TransactionCategoryService,
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
}
