package io.github.salomax.neotool.banking.domain.issuer

import java.util.UUID

/**
 * Domain repository for banking issuers.
 * Queries are intentionally read-only for the application layer;
 * write access is limited to the IssuerLogoHydrationJob.
 */
interface IssuerRepository {
    fun findAllActive(): List<Issuer>
    fun findAllPendingLogoHydration(): List<Issuer>
    fun findById(id: UUID): Issuer?
    fun findByIds(ids: Collection<UUID>): List<Issuer>
    fun updateLogoKey(id: UUID, logoKey: String)
}
