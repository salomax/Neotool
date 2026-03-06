package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.BillStatus
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA entity for banking.credit_card_bills.
 * id is DB-generated via uuidv7() (insertable = false).
 */
@Entity
@Table(name = "credit_card_bills", schema = "banking")
open class CreditCardBillEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "financial_account_id", nullable = false)
    open var financialAccountId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "closing_date", nullable = false)
    open var closingDate: LocalDate,
    @Column(name = "due_date", nullable = false)
    open var dueDate: LocalDate,
    @Column(name = "total_amount", precision = 18, scale = 4)
    open var totalAmount: BigDecimal? = null,
    @Column(name = "minimum_payment", precision = 18, scale = 4)
    open var minimumPayment: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    open var status: BillStatus = BillStatus.OPEN,
    @Column(name = "provider_bill_id")
    open var providerBillId: String? = null,
    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
