package io.github.salomax.neotool.banking.model.transaction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "transaction_tags", schema = "banking")
@IdClass(TransactionTagId::class)
open class TransactionTagEntity(
    @Id
    @Column(name = "transaction_id", nullable = false)
    open var transactionId: UUID,
    @Id
    @Column(name = "tag", nullable = false)
    open var tag: String,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
)
