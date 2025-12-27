package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.service.PrincipalType
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Repository
interface PrincipalRepository : JpaRepository<PrincipalEntity, UUID> {
    /**
     * Find a principal by type and external ID.
     * For user principals, external_id is the user UUID.
     * For service principals, external_id is the service identifier.
     */
    fun findByPrincipalTypeAndExternalId(
        principalType: PrincipalType,
        externalId: String,
    ): Optional<PrincipalEntity>

    /**
     * Find all principals of a given type.
     */
    fun findByPrincipalType(principalType: PrincipalType): List<PrincipalEntity>

    /**
     * Batch fetch principals by type and external IDs.
     * Useful for avoiding N+1 queries when fetching enabled status for multiple users.
     *
     * @param principalType The type of principals to fetch (e.g., USER, SERVICE)
     * @param externalIds List of external IDs (e.g., user UUIDs as strings)
     * @return List of matching principals
     */
    fun findByPrincipalTypeAndExternalIdIn(
        principalType: PrincipalType,
        externalIds: List<String>,
    ): List<PrincipalEntity>
}
