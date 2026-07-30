package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.model.OAuth2ConsentEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface OAuth2ConsentRepository : JpaRepository<OAuth2ConsentEntity, UUID> {
    fun findByAccountIdAndRevokedAtIsNullOrderByLastGrantedAtDesc(accountId: UUID): List<OAuth2ConsentEntity>

    fun findOneByIdAndRevokedAtIsNull(id: UUID): Optional<OAuth2ConsentEntity>

    fun findOneByUserIdAndClientIdAndAccountIdAndRevokedAtIsNull(
        userId: UUID,
        clientId: String,
        accountId: UUID,
    ): Optional<OAuth2ConsentEntity>
}
