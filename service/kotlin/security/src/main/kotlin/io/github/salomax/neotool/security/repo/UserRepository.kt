package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.UserEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmail(email: String): UserEntity?

    fun findByRememberMeToken(token: String): UserEntity?

    fun findByPasswordResetToken(token: String): UserEntity?

    /**
     * Find all users by their IDs.
     */
    fun findByIdIn(ids: List<UUID>): List<UserEntity>

    /**
     * Find all users with cursor-based pagination, ordered by displayName (or email if displayName is null) ascending.
     *
     * @param first Maximum number of results to return
     * @param after Cursor (UUID) to start after (exclusive)
     * @return List of users ordered by name ascending
     */
    @Query(
        value = """
        SELECT * FROM security.users
        WHERE (:after IS NULL OR id > :after)
        ORDER BY COALESCE(display_name, email) ASC, id ASC
        LIMIT :first
        """,
        nativeQuery = true,
    )
    fun findAll(
        first: Int,
        after: UUID?,
    ): List<UserEntity>

    /**
     * Search users by name or email with cursor-based pagination.
     * Performs case-insensitive partial matching on displayName and email fields.
     * Results are ordered by displayName (or email if displayName is null) ascending.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param first Maximum number of results to return
     * @param after Cursor (UUID) to start after (exclusive)
     * @return List of matching users ordered by name ascending
     */
    @Query(
        value = """
        SELECT * FROM security.users
        WHERE (LOWER(COALESCE(display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(email) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:after IS NULL OR id > :after)
        ORDER BY COALESCE(display_name, email) ASC, id ASC
        LIMIT :first
        """,
        nativeQuery = true,
    )
    fun searchByNameOrEmail(
        query: String,
        first: Int,
        after: UUID?,
    ): List<UserEntity>
}
