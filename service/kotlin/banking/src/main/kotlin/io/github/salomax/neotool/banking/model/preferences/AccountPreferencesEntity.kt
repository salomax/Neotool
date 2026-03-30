package io.github.salomax.neotool.banking.model.preferences

import io.github.salomax.neotool.banking.domain.preferences.RuleApplicationMode
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "account_preferences", schema = "banking")
open class AccountPreferencesEntity(
    @Id
    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    open var accountId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_application", nullable = false, length = 24)
    open var ruleApplicationMode: RuleApplicationMode,
    @Enumerated(EnumType.STRING)
    @Column(name = "default_txn_status", nullable = false, length = 16)
    open var defaultTransactionStatus: TransactionStatus,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
)
