package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.ProductAlias
import java.util.UUID

interface ProductAliasRepository {
    fun findById(
        id: UUID,
        accountId: UUID,
    ): ProductAlias?

    fun findByAccountIdAndRawDescription(
        accountId: UUID,
        rawDescription: String,
    ): ProductAlias?

    fun save(alias: ProductAlias): ProductAlias

    fun deleteById(id: UUID)
}
