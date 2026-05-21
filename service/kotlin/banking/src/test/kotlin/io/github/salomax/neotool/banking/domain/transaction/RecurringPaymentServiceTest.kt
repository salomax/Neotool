package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import io.github.salomax.neotool.common.error.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RecurringPaymentServiceTest {
    private val repository: RecurringPaymentRepository = mock()
    private val service = RecurringPaymentService(repository)

    private val accountId = UUID.randomUUID()
    private val now = Instant.now()

    @Test
    fun `computeNextDueDate MONTHLY clamps Jan 31 to Feb end`() {
        val template =
            recurringTemplate(
                frequency = RecurringFrequency.MONTHLY,
                dayOfMonth = 31,
                nextDueDate = LocalDate.of(2025, 1, 31),
            )

        val next = service.computeNextDueDate(template)

        assertThat(next).isEqualTo(LocalDate.of(2025, 2, 28))
    }

    @Test
    fun `computeNextDueDate MONTHLY clamps Mar 31 to Apr 30`() {
        val template =
            recurringTemplate(
                frequency = RecurringFrequency.MONTHLY,
                dayOfMonth = 31,
                nextDueDate = LocalDate.of(2025, 3, 31),
            )

        val next = service.computeNextDueDate(template)

        assertThat(next).isEqualTo(LocalDate.of(2025, 4, 30))
    }

    @Test
    fun `computeNextDueDate WEEKLY uses weekly default interval`() {
        val template =
            recurringTemplate(
                frequency = RecurringFrequency.WEEKLY,
                intervalDays = null,
                nextDueDate = LocalDate.of(2025, 3, 1),
            )

        val next = service.computeNextDueDate(template)

        assertThat(next).isEqualTo(LocalDate.of(2025, 3, 8))
    }

    @Test
    fun `computeNextDueDate CUSTOM uses provided intervalDays`() {
        val template =
            recurringTemplate(
                frequency = RecurringFrequency.CUSTOM,
                intervalDays = 14,
                nextDueDate = LocalDate.of(2025, 3, 1),
            )

        val next = service.computeNextDueDate(template)

        assertThat(next).isEqualTo(LocalDate.of(2025, 3, 15))
    }

    @Test
    fun `confirmPayment on due date advances one cycle`() {
        val template =
            recurringTemplate(
                id = UUID.randomUUID(),
                frequency = RecurringFrequency.MONTHLY,
                dayOfMonth = 10,
                nextDueDate = LocalDate.of(2025, 5, 10),
            )
        val updateCaptor = argumentCaptor<RecurringPayment>()
        whenever(repository.update(updateCaptor.capture())).thenAnswer { it.arguments[0] as RecurringPayment }

        val updated = service.confirmPayment(template, paymentDate = LocalDate.of(2025, 5, 10))

        assertThat(updated.lastPaidDate).isEqualTo(LocalDate.of(2025, 5, 10))
        assertThat(updated.nextDueDate).isEqualTo(LocalDate.of(2025, 6, 10))
        verify(repository).update(any())
    }

    @Test
    fun `confirmPayment for delayed payment advances multiple cycles`() {
        val template =
            recurringTemplate(
                id = UUID.randomUUID(),
                frequency = RecurringFrequency.MONTHLY,
                dayOfMonth = 10,
                nextDueDate = LocalDate.of(2025, 5, 10),
            )
        val updateCaptor = argumentCaptor<RecurringPayment>()
        whenever(repository.update(updateCaptor.capture())).thenAnswer { it.arguments[0] as RecurringPayment }

        val updated = service.confirmPayment(template, paymentDate = LocalDate.of(2025, 6, 15))

        assertThat(updated.lastPaidDate).isEqualTo(LocalDate.of(2025, 6, 15))
        assertThat(updated.nextDueDate).isEqualTo(LocalDate.of(2025, 7, 10))
        assertThat(updateCaptor.firstValue.nextDueDate).isEqualTo(LocalDate.of(2025, 7, 10))
        verify(repository).update(any())
    }

    @Test
    fun `validateTemplate rejects blank name`() {
        val template = recurringTemplate(name = "   ", nextDueDate = LocalDate.of(2025, 5, 10))

        assertThatThrownBy { service.validateTemplate(template) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateTemplate rejects remindDaysBefore out of range`() {
        val tooHigh = recurringTemplate(remindDaysBefore = 61, nextDueDate = LocalDate.of(2025, 5, 10))
        val negative = recurringTemplate(remindDaysBefore = -1, nextDueDate = LocalDate.of(2025, 5, 10))

        assertThatThrownBy { service.validateTemplate(tooHigh) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { service.validateTemplate(negative) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateTemplate rejects non-positive expectedAmount`() {
        val zero = recurringTemplate(expectedAmount = BigDecimal.ZERO, nextDueDate = LocalDate.of(2025, 5, 10))
        val negative = recurringTemplate(expectedAmount = BigDecimal("-1.00"), nextDueDate = LocalDate.of(2025, 5, 10))

        assertThatThrownBy { service.validateTemplate(zero) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { service.validateTemplate(negative) }
            .isInstanceOf(ValidationException::class.java)
    }

    private fun recurringTemplate(
        id: UUID? = null,
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        dayOfMonth: Int? = 10,
        intervalDays: Int? = null,
        remindDaysBefore: Int = 3,
        name: String = "Internet",
        expectedAmount: BigDecimal? = BigDecimal("100.00"),
        nextDueDate: LocalDate,
    ): RecurringPayment =
        RecurringPayment(
            id = id,
            accountId = accountId,
            financialAccountId = null,
            name = name,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            expectedAmount = expectedAmount,
            currencyCode = "BRL",
            categoryId = null,
            merchantId = null,
            frequency = frequency,
            dayOfMonth = dayOfMonth,
            intervalDays = intervalDays,
            remindDaysBefore = remindDaysBefore,
            status = RecurringPaymentStatus.ACTIVE,
            nextDueDate = nextDueDate,
            lastPaidDate = null,
            notes = null,
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
        )
}
