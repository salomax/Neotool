package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.RefreshTokenEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {
    /**
     * Find refresh token by its hash.
     */
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    /**
     * Find all tokens in a token family.
     */
    fun findByFamilyId(familyId: UUID): List<RefreshTokenEntity>

    /**
     * Find all active (non-revoked) refresh tokens for a user.
     */
    fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<RefreshTokenEntity>

    /**
     * Find active token by user ID and token hash.
     */
    fun findByUserIdAndTokenHashAndRevokedAtIsNull(
        userId: UUID,
        tokenHash: String,
    ): RefreshTokenEntity?
}
