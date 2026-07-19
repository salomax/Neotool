package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.model.OAuth2AuthorizationCodeEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface OAuth2AuthorizationCodeRepository : JpaRepository<OAuth2AuthorizationCodeEntity, UUID> {
    fun findByCodeHash(codeHash: String): OAuth2AuthorizationCodeEntity?

    @Query(
        value = """
        UPDATE security.oauth2_authorization_codes
        SET consumed_at = :consumedAt,
            updated_at = :consumedAt,
            version = version + 1
        WHERE code_hash = :codeHash
          AND consumed_at IS NULL
          AND expires_at > :consumedAt
        """,
        nativeQuery = true,
    )
    fun consumeIfActive(
        codeHash: String,
        consumedAt: Instant,
    ): Long

    @Query(
        value = """
        UPDATE security.oauth2_authorization_codes
        SET issued_access_token_jti = :accessTokenJti,
            issued_refresh_token_family_id = :refreshTokenFamilyId,
            updated_at = :updatedAt,
            version = version + 1
        WHERE code_hash = :codeHash
        """,
        nativeQuery = true,
    )
    fun markIssuedTokens(
        codeHash: String,
        accessTokenJti: String?,
        refreshTokenFamilyId: UUID,
        updatedAt: Instant,
    ): Long
}
