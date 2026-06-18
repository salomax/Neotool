package io.github.salomax.neotool.banking.model.preferences

import io.github.salomax.neotool.banking.domain.preferences.RuleCreationMode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_preferences", schema = "banking")
open class UserRuleCreationModeEntity(
    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    open var userId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_creation_mode", nullable = false, length = 20)
    open var ruleCreationMode: RuleCreationMode = RuleCreationMode.ALWAYS_PROMPT,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
