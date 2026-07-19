package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestEntity
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestStatus
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface OAuth2AuthorizationRequestRepository : JpaRepository<OAuth2AuthorizationRequestEntity, UUID> {
    fun findByIdAndStatus(
        id: UUID,
        status: OAuth2AuthorizationRequestStatus,
    ): OAuth2AuthorizationRequestEntity?
}
