package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.model.issuer.IssuerMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Default implementation of [IssuerRepository] using JPA.
 */
@Singleton
open class DefaultIssuerRepository(
    private val entityRepository: IssuerEntityRepository,
) : IssuerRepository {

    override fun findAllActive(): List<Issuer> =
        entityRepository.findByIsActiveTrue().map(IssuerMapper::toDomain)

    override fun findAllPendingLogoHydration(): List<Issuer> =
        entityRepository.findByLogoKeyIsNullAndDomainIsNotNull().map(IssuerMapper::toDomain)

    override fun findById(id: UUID): Issuer? =
        entityRepository.findById(id).orElse(null)?.let(IssuerMapper::toDomain)

    override fun findByIds(ids: Collection<UUID>): List<Issuer> =
        if (ids.isEmpty()) emptyList()
        else entityRepository.findByIdIn(ids).map(IssuerMapper::toDomain)

    @Transactional
    override fun updateLogoKey(id: UUID, logoKey: String) {
        val entity = entityRepository.findById(id)
            .orElseThrow { NoSuchElementException("Issuer $id not found") }
        entity.logoKey = logoKey
        // Entity stays managed within the transaction; Hibernate issues UPDATE via dirty checking.
    }
}
