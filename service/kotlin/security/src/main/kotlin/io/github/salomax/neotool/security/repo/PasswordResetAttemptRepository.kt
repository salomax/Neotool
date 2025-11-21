package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.PasswordResetAttemptEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface PasswordResetAttemptRepository : JpaRepository<PasswordResetAttemptEntity, UUID> {
    @Query("SELECT p FROM PasswordResetAttemptEntity p WHERE p.email = :email AND p.windowStart > :windowStart")
    fun findByEmailAndWindowStartGreaterThan(email: String, windowStart: Instant): List<PasswordResetAttemptEntity>
    
    fun findByCreatedAtLessThan(cutoff: Instant): List<PasswordResetAttemptEntity>
}

