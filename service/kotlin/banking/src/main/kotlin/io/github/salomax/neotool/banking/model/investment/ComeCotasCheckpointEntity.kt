package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Derived checkpoint state intentionally uses hard-delete-by-cascade from the holding.
 * Unlike ledger entities, this table is replayable cache/state, so a deleted_at column
 * would preserve data the engine is expected to rebuild instead of retain.
 */
@Entity
@Table(name = "come_cotas_checkpoint", schema = "banking")
open class ComeCotasCheckpointEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "holding_id", nullable = false)
    open var holdingId: UUID,
    @Column(name = "checkpoint_date", nullable = false)
    open var checkpointDate: LocalDate,
    @Column(name = "computed_amount", nullable = false, precision = 18, scale = 4)
    open var computedAmount: BigDecimal,
    @Column(name = "withheld_amount", nullable = false, precision = 18, scale = 4)
    open var withheldAmount: BigDecimal,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
