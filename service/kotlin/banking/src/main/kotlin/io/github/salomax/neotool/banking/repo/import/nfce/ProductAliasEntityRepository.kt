package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.model.import.nfce.ProductAliasEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ProductAliasEntityRepository : JpaRepository<ProductAliasEntity, UUID> {
    fun findByIdAndAccountId(
        id: UUID,
        accountId: UUID,
    ): ProductAliasEntity?

    fun findByAccountIdAndRawDescription(
        accountId: UUID,
        rawDescription: String,
    ): ProductAliasEntity?
}
