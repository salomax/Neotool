package io.github.salomax.neotool.security.model.preferences

import io.github.salomax.neotool.security.domain.preferences.Language
import io.github.salomax.neotool.security.domain.preferences.Theme
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_preferences", schema = "security")
open class UserPreferencesEntity(
    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    open var userId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    open var language: Language,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    open var theme: Theme,
    @Column(name = "blur_balances", nullable = false)
    open var blurBalances: Boolean = false,
    @Column(name = "first_day_of_week", nullable = false)
    open var firstDayOfWeek: Short = 1,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
