package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.Product
import java.util.UUID

interface ProductRepository {
    fun findById(
        id: UUID,
        accountId: UUID,
    ): Product?

    fun findByIds(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<Product>

    fun list(
        accountId: UUID,
        search: String? = null,
        category: String? = null,
    ): List<Product>

    fun findByName(
        accountId: UUID,
        name: String,
    ): Product?

    fun save(product: Product): Product
}
