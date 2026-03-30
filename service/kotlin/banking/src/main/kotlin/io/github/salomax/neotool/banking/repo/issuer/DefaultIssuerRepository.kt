package io.github.salomax.neotool.banking.repo.issuer

import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.domain.issuer.IssuerCategory
import io.github.salomax.neotool.banking.repo.issuer.IssuerRepository
import io.github.salomax.neotool.banking.model.issuer.IssuerMapper
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Default implementation of [IssuerRepository] using JPA.
 */
@Singleton
open class DefaultIssuerRepository(
    private val entityRepository: IssuerEntityRepository,
) : IssuerRepository {
    override fun findAllActive(): List<Issuer> = entityRepository.findByIsActiveTrue().map(IssuerMapper::toDomain)

    override fun findActiveByCategories(categories: List<IssuerCategory>): List<Issuer> =
        if (categories.isEmpty()) {
            findAllActive()
        } else {
            entityRepository.findByIsActiveTrueAndCategoryIn(categories).map(IssuerMapper::toDomain)
        }

    override fun findById(id: UUID): Issuer? = entityRepository.findById(id).orElse(null)?.let(IssuerMapper::toDomain)

    override fun findByIds(ids: Collection<UUID>): List<Issuer> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            entityRepository.findByIdIn(ids).map(IssuerMapper::toDomain)
        }
}
