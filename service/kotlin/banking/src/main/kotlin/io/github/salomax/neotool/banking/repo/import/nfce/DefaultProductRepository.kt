package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.Product
import io.github.salomax.neotool.banking.model.import.nfce.ProductMapper
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
open class DefaultProductRepository(
    private val entityRepository: ProductEntityRepository,
) : ProductRepository {
    override fun findById(
        id: UUID,
        accountId: UUID,
    ): Product? = entityRepository.findByIdAndAccountId(id, accountId)?.let(ProductMapper::toDomain)

    override fun findByIds(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<Product> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            entityRepository.findByIdInAndAccountId(ids, accountId).map(ProductMapper::toDomain)
        }

    override fun list(
        accountId: UUID,
        search: String?,
        category: String?,
    ): List<Product> = entityRepository.listForAccount(accountId, search?.trim(), category?.trim()).map(ProductMapper::toDomain)

    override fun findByName(
        accountId: UUID,
        name: String,
    ): Product? = entityRepository.findByAccountIdAndExactName(accountId, name.trim())?.let(ProductMapper::toDomain)

    override fun save(product: Product): Product = entityRepository.save(ProductMapper.toEntity(product)).let(ProductMapper::toDomain)
}
