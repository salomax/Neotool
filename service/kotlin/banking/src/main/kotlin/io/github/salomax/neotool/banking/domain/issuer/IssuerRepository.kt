package io.github.salomax.neotool.banking.domain.issuer

import java.util.UUID

/**
 * Domain repository for banking issuers.
 * Queries are intentionally read-only for the application layer.
 */
interface IssuerRepository {
    fun findAllActive(): List<Issuer>

    fun findActiveByCategories(categories: List<IssuerCategory>): List<Issuer>

    fun findById(id: UUID): Issuer?

    fun findByIds(ids: Collection<UUID>): List<Issuer>
}
