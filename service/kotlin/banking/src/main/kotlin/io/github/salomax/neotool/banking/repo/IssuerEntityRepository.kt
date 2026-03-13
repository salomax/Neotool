package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.issuer.IssuerCategory
import io.github.salomax.neotool.banking.model.issuer.IssuerEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for banking.issuers.
 */
@Repository
interface IssuerEntityRepository : JpaRepository<IssuerEntity, UUID> {
    fun findByIsActiveTrue(): List<IssuerEntity>

    fun findByIsActiveTrueAndCategoryIn(categories: Collection<IssuerCategory>): List<IssuerEntity>

    fun findByLogoKeyIsNullAndDomainIsNotNull(): List<IssuerEntity>

    fun findByIdIn(ids: Collection<UUID>): List<IssuerEntity>
}
