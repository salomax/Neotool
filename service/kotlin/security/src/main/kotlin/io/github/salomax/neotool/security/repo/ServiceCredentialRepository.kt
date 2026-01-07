package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.ServiceCredentialEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Repository
interface ServiceCredentialRepository : JpaRepository<ServiceCredentialEntity, UUID> {
    /**
     * Find service credential by principal ID.
     *
     * @param principalId The principal ID (which is the primary key)
     * @return Optional ServiceCredentialEntity if found
     */
    fun findByPrincipalId(principalId: UUID): Optional<ServiceCredentialEntity>
}

