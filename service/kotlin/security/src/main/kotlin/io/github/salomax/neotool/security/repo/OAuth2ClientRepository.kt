package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.model.OAuth2ClientEntity
import io.github.salomax.neotool.security.model.OAuth2ClientStatus
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant

@TraceExecution(layer = "repository")
@Repository
interface OAuth2ClientRepository : JpaRepository<OAuth2ClientEntity, String> {
    fun findByIdAndStatus(
        id: String,
        status: OAuth2ClientStatus,
    ): OAuth2ClientEntity?

    @Query(
        value = """
        UPDATE security.oauth2_clients
        SET last_used_at = :usedAt,
            updated_at = :usedAt,
            version = version + 1
        WHERE client_id = :clientId
          AND status = 'ACTIVE'
        """,
        nativeQuery = true,
    )
    fun markUsed(
        clientId: String,
        usedAt: Instant,
    ): Long

    @Query(
        value = """
        UPDATE security.oauth2_clients
        SET status = 'DELETED',
            updated_at = :deletedAt,
            version = version + 1
        WHERE status = 'ACTIVE'
          AND last_used_at IS NULL
          AND client_id_issued_at < :issuedBefore
        """,
        nativeQuery = true,
    )
    fun markUnusedClientsDeleted(
        issuedBefore: Instant,
        deletedAt: Instant,
    ): Long
}
