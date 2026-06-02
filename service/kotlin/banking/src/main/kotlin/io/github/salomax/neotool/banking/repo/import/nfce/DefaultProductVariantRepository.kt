package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.ProductVariant
import io.github.salomax.neotool.banking.model.import.nfce.ProductVariantMapper
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultProductVariantRepository(
    private val entityRepository: ProductVariantEntityRepository,
) : ProductVariantRepository {
    override fun findById(
        id: UUID,
        accountId: UUID,
    ): ProductVariant? = entityRepository.findByIdAndAccountId(id, accountId)?.let(ProductVariantMapper::toDomain)

    override fun findByIds(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<ProductVariant> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            entityRepository.findByIdInAndAccountId(ids, accountId).map(ProductVariantMapper::toDomain)
        }

    override fun findByProductIds(
        productIds: Collection<UUID>,
        accountId: UUID,
    ): List<ProductVariant> =
        if (productIds.isEmpty()) {
            emptyList()
        } else {
            entityRepository.findByProductIdInAndAccountIdOrderByNameAsc(
                productIds,
                accountId,
            ).map(ProductVariantMapper::toDomain)
        }

    override fun search(
        accountId: UUID,
        search: String?,
        limit: Int,
    ): List<ProductVariant> =
        entityRepository.searchForAccount(accountId, search?.trim(), limit.coerceIn(1, 50))
            .map(ProductVariantMapper::toDomain)

    override fun save(variant: ProductVariant): ProductVariant =
        entityRepository.save(ProductVariantMapper.toEntity(variant)).let(ProductVariantMapper::toDomain)
}
