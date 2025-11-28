package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.domain.abac.PolicyEffect
import io.github.salomax.neotool.security.model.abac.AbacPolicyEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Repository
interface AbacPolicyRepository : JpaRepository<AbacPolicyEntity, UUID> {
    /**
     * Find all active ABAC policies.
     */
    @Query(
        """
        SELECT p FROM AbacPolicyEntity p
        WHERE p.isActive = true
        ORDER BY p.name
        """,
    )
    fun findActivePolicies(): List<AbacPolicyEntity>

    /**
     * Find active policies by effect type.
     */
    @Query(
        """
        SELECT p FROM AbacPolicyEntity p
        WHERE p.isActive = true
        AND p.effect = :effect
        ORDER BY p.name
        """,
    )
    fun findActivePoliciesByEffect(effect: PolicyEffect): List<AbacPolicyEntity>

    /**
     * Find a policy by name.
     */
    fun findByName(name: String): Optional<AbacPolicyEntity>
}
