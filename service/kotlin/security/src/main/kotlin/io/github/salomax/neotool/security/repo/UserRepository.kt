package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.UserEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmail(email: String): UserEntity?

    fun findByRememberMeToken(token: String): UserEntity?

    fun findByPasswordResetToken(token: String): UserEntity?

    fun findByIdIn(ids: List<UUID>): List<UserEntity>
}
