package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.model.transaction.TransactionCategoryEntity
import io.github.salomax.neotool.banking.model.transaction.TransactionMapper
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class DefaultTransactionCategoryRepository(
    private val entityRepository: TransactionCategoryEntityRepository,
) : TransactionCategoryRepository {
    override fun findAll(accountId: UUID): List<TransactionCategory> =
        entityRepository
            .findByAccountIdIsNullOrAccountIdAndDeletedAtIsNull(accountId)
            .map(TransactionMapper::toDomain)

    override fun findAccessibleByIds(
        ids: Set<UUID>,
        accountId: UUID,
    ): List<TransactionCategory> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            entityRepository.findAccessibleByIds(ids, accountId).map(TransactionMapper::toDomain)
        }

    override fun findById(id: UUID): TransactionCategory? =
        entityRepository.findByIdAndDeletedAtIsNull(id)?.let(TransactionMapper::toDomain)

    override fun save(category: TransactionCategory): TransactionCategory {
        val entity: TransactionCategoryEntity =
            if (category.id == null) {
                TransactionMapper.toEntity(category)
            } else {
                val existing =
                    entityRepository.findByIdAndDeletedAtIsNull(category.id)
                        ?: TransactionMapper.toEntity(category)
                existing.apply {
                    accountId = category.accountId
                    parentId = category.parentId
                    name = category.name
                    applicableTo = category.applicableTo
                    icon = category.icon
                    color = category.color
                    isSystem = category.isSystem
                    sortOrder = category.sortOrder
                    deletedAt = category.deletedAt
                    createdAt = category.createdAt
                    updatedAt = category.updatedAt
                }
            }

        return TransactionMapper.toDomain(entityRepository.save(entity))
    }

    override fun existsByNameAndParentIdAndAccountIdAndDeletedAtIsNull(
        name: String,
        parentId: UUID?,
        accountId: UUID,
    ): Boolean =
        entityRepository.existsByNameAndParentIdAndAccountIdAndDeletedAtIsNull(
            name = name,
            parentId = parentId,
            accountId = accountId,
        )

    override fun maxSortOrder(
        accountId: UUID,
        parentId: UUID?,
    ): Int? =
        entityRepository.findMaxSortOrder(
            accountId = accountId,
            parentId = parentId,
        )

    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ) {
        entityRepository.softDelete(id = id, accountId = accountId, now = Instant.now())
    }

    override fun softDeleteChildren(
        parentId: UUID,
        accountId: UUID,
    ) {
        entityRepository.softDeleteChildren(parentId = parentId, accountId = accountId, now = Instant.now())
    }
}
