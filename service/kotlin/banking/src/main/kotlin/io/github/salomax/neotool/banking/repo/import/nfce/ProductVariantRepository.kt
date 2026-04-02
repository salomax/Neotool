package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.ProductVariant
import java.util.UUID

interface ProductVariantRepository {
    fun findById(
        id: UUID,
        accountId: UUID,
    ): ProductVariant?

    fun findByIds(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<ProductVariant>

    fun findByProductIds(
        productIds: Collection<UUID>,
        accountId: UUID,
    ): List<ProductVariant>

    fun search(
        accountId: UUID,
        search: String?,
        limit: Int,
    ): List<ProductVariant>

    fun save(variant: ProductVariant): ProductVariant
}
