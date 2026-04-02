package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.ProductAlias
import io.github.salomax.neotool.banking.model.import.nfce.ProductAliasMapper
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultProductAliasRepository(
    private val entityRepository: ProductAliasEntityRepository,
) : ProductAliasRepository {
    override fun findById(
        id: UUID,
        accountId: UUID,
    ): ProductAlias? = entityRepository.findByIdAndAccountId(id, accountId)?.let(ProductAliasMapper::toDomain)

    override fun findByAccountIdAndRawDescription(
        accountId: UUID,
        rawDescription: String,
    ): ProductAlias? = entityRepository.findByAccountIdAndRawDescription(accountId, rawDescription)?.let(ProductAliasMapper::toDomain)

    override fun save(alias: ProductAlias): ProductAlias =
        entityRepository.save(ProductAliasMapper.toEntity(alias)).let(ProductAliasMapper::toDomain)

    override fun deleteById(id: UUID) {
        entityRepository.deleteById(id)
    }
}
