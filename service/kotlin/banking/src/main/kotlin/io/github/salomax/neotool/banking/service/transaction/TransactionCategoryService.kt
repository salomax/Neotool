package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableToMismatchException
import io.github.salomax.neotool.banking.domain.transaction.CategoryDepthExceededException
import io.github.salomax.neotool.banking.domain.transaction.CategoryNotFoundException
import io.github.salomax.neotool.banking.domain.transaction.DuplicateCategoryNameException
import io.github.salomax.neotool.banking.domain.transaction.SystemCategoryNotEditableException
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.model.dto.transaction.CreateCategoryInput
import io.github.salomax.neotool.banking.model.dto.transaction.UpdateCategoryInput
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@Singleton
open class TransactionCategoryService(
    private val categoryRepository: TransactionCategoryRepository,
) {
    fun create(
        input: CreateCategoryInput,
        accountId: UUID,
    ): TransactionCategory {
        val now = Instant.now()

        val parent =
            input.parentId?.let { parentId ->
                categoryRepository.findById(parentId)
                    ?: throw CategoryNotFoundException(parentId)
            }

        if (parent != null && parent.parentId != null) {
            throw CategoryDepthExceededException()
        }

        if (parent != null && !isCompatible(input.applicableTo, parent.applicableTo)) {
            throw CategoryApplicableToMismatchException(input.applicableTo, parent.applicableTo)
        }

        val exists =
            categoryRepository.existsByNameAndParentIdAndAccountIdAndDeletedAtIsNull(
                name = input.name,
                parentId = input.parentId,
                accountId = accountId,
            )
        if (exists) {
            throw DuplicateCategoryNameException(input.name)
        }

        val sortOrder =
            input.sortOrder
                ?: categoryRepository.maxSortOrder(accountId = accountId, parentId = input.parentId)?.let { it + 10 }
                ?: 10

        val category =
            TransactionCategory(
                id = null,
                accountId = accountId,
                parentId = input.parentId,
                name = input.name,
                applicableTo = input.applicableTo,
                icon = input.icon,
                color = input.color,
                isSystem = false,
                sortOrder = sortOrder,
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
            )

        return categoryRepository.save(category)
    }

    fun update(
        id: UUID,
        input: UpdateCategoryInput,
        accountId: UUID,
    ): TransactionCategory {
        val now = Instant.now()
        val category =
            categoryRepository.findById(id)
                ?: throw CategoryNotFoundException(id)

        if (category.isSystem) {
            throw SystemCategoryNotEditableException()
        }

        if (category.accountId != accountId) {
            throw CategoryNotFoundException(id)
        }

        val newName = input.name ?: category.name
        if (input.name != null && input.name != category.name) {
            val exists =
                categoryRepository.existsByNameAndParentIdAndAccountIdAndDeletedAtIsNull(
                    name = newName,
                    parentId = category.parentId,
                    accountId = accountId,
                )
            if (exists) {
                throw DuplicateCategoryNameException(newName)
            }
        }

        val updated =
            category.copy(
                name = newName,
                icon = input.icon ?: category.icon,
                color = input.color ?: category.color,
                sortOrder = input.sortOrder ?: category.sortOrder,
                updatedAt = now,
            )

        return categoryRepository.save(updated)
    }

    @Transactional
    open fun delete(
        id: UUID,
        accountId: UUID,
    ): Boolean {
        val category =
            categoryRepository.findById(id)
                ?: throw CategoryNotFoundException(id)

        if (category.isSystem) {
            throw SystemCategoryNotEditableException()
        }

        if (category.accountId != accountId) {
            throw CategoryNotFoundException(id)
        }

        if (category.parentId == null) {
            categoryRepository.softDeleteChildren(id, accountId)
        }
        categoryRepository.softDelete(id, accountId)

        return true
    }

    private fun isCompatible(
        child: CategoryApplicableTo,
        parent: CategoryApplicableTo,
    ): Boolean = parent == CategoryApplicableTo.ANY || child == parent || child == CategoryApplicableTo.ANY
}
