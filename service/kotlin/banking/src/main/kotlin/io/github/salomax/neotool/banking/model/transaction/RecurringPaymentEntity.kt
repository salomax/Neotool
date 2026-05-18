package io.github.salomax.neotool.banking.model.transaction

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

@Entity
@Table(name = "recurring_payments", schema = "banking")
open class RecurringPaymentEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "financial_account_id")
    open var financialAccountId: UUID? = null,
    @Column(nullable = false)
    open var name: String,
    @Column(name = "credit_debit_type", nullable = false, length = 10)
    open var creditDebitType: String,
    @Column
    open var type: String? = null,
    @Column(name = "expected_amount", precision = 19, scale = 4)
    open var expectedAmount: BigDecimal? = null,
    @Column(name = "currency_code", nullable = false, length = 3)
    open var currencyCode: String = "BRL",
    @Column(name = "category_id")
    open var categoryId: UUID? = null,
    @Column(name = "merchant_id")
    open var merchantId: UUID? = null,
    @Column(nullable = false, length = 20)
    open var frequency: String,
    @Column(name = "day_of_month")
    open var dayOfMonth: Short? = null,
    @Column(name = "interval_days")
    open var intervalDays: Int? = null,
    @Column(name = "remind_days_before", nullable = false)
    open var remindDaysBefore: Short = 3,
    @Column(nullable = false, length = 20)
    open var status: String = "ACTIVE",
    @Column(name = "next_due_date", nullable = false)
    open var nextDueDate: LocalDate,
    @Column(name = "last_paid_date")
    open var lastPaidDate: LocalDate? = null,
    @Column
    open var notes: String? = null,
    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
