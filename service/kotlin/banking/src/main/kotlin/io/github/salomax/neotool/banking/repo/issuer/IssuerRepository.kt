package io.github.salomax.neotool.banking.repo.issuer

import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.domain.issuer.IssuerCategory
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
