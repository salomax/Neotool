package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mcp_write_idempotency", schema = "banking")
open class McpWriteIdempotencyEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "idempotency_key", nullable = false, length = 255)
    open var idempotencyKey: String,
    @Column(nullable = false, length = 100)
    open var operation: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb", nullable = false)
    open var resultJson: String = "{}",
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
