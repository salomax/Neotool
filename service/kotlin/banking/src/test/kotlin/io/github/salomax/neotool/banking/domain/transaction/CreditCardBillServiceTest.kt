package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.service.transaction.*
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CreditCardBillServiceTest {
    private val repository: CreditCardBillRepository = mock()
    private val service = CreditCardBillService(repository)

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val now = Instant.now()

    // -------------------------------------------------------------------------
    // computeClosingDate
    // -------------------------------------------------------------------------

    @Test
    fun `computeClosingDate - on closing day returns same month`() {
        val date = LocalDate.of(2024, 2, 7) // Feb 7
        val closingDay = 7

        val result = service.computeClosingDate(date, closingDay)

        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 7))
    }

    @Test
    fun `computeClosingDate - after closing day returns next month`() {
        val date = LocalDate.of(2024, 2, 8) // Feb 8
        val closingDay = 7

        val result = service.computeClosingDate(date, closingDay)

        assertThat(result).isEqualTo(LocalDate.of(2024, 3, 7))
    }

    @Test
    fun `computeClosingDate - closingDay 31 in Feb is clamped`() {
        // Feb 2024 has 29 days (leap year)
        val date = LocalDate.of(2024, 2, 10)
        val closingDay = 31

        val result = service.computeClosingDate(date, closingDay)

        // Should be Feb 29 because 10 <= 29
        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 29))
    }

    @Test
    fun `computeClosingDate - closingDay 31 in Jan returns Jan 31`() {
        val date = LocalDate.of(2024, 1, 15)
        val closingDay = 31

        val result = service.computeClosingDate(date, closingDay)

        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 31))
    }

    // -------------------------------------------------------------------------
    // computeDueDate
    // -------------------------------------------------------------------------

    @Test
    fun `computeDueDate - due day after closing returns same month`() {
        val closingDate = LocalDate.of(2024, 2, 7)
        val dueDay = 15

        val result = service.computeDueDate(closingDate, dueDay)

        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 15))
    }

    @Test
    fun `computeDueDate - due day before closing returns next month`() {
        val closingDate = LocalDate.of(2024, 2, 25)
        val dueDay = 10

        val result = service.computeDueDate(closingDate, dueDay)

        assertThat(result).isEqualTo(LocalDate.of(2024, 3, 10))
    }

    @Test
    fun `computeDueDate - due day end-of-month clamp`() {
        // Closing Jan 31, due day 31
        // Next month is Feb (29 days in 2024)
        // If logic pushes to next month, it should be Feb 29
        val closingDate = LocalDate.of(2024, 1, 31)
        val dueDay = 31

        val result = service.computeDueDate(closingDate, dueDay)

        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 29))
    }

    // -------------------------------------------------------------------------
    // resolveBillForTransaction
    // -------------------------------------------------------------------------

    @Test
    fun `resolveBillForTransaction - non-card account returns null`() {
        val account =
            FinancialAccount(
                id = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                type = FinancialAccountType.BANK,
                subtype = FinancialAccountSubtype.CONTA_CORRENTE,
                name = "Bank",
                currencyCode = "BRL",
                cardData = null,
                createdAt = now,
                updatedAt = now,
            )

        val result =
            service.resolveBillForTransaction(
                account,
                LocalDate.now(),
                TransactionType.CARD,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `resolveBillForTransaction - BALANCE_ADJUSTMENT returns null`() {
        val account = creditCardAccount()

        val result =
            service.resolveBillForTransaction(
                account,
                LocalDate.now(),
                TransactionType.BALANCE_ADJUSTMENT,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `resolveBillForTransaction - no cardData returns null`() {
        val account =
            FinancialAccount(
                id = financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                type = FinancialAccountType.CREDIT,
                subtype = FinancialAccountSubtype.CREDIT_CARD,
                name = "Card",
                currencyCode = "BRL",
                // Missing
                cardData = null,
                createdAt = now,
                updatedAt = now,
            )

        val result =
            service.resolveBillForTransaction(
                account,
                LocalDate.now(),
                TransactionType.CARD,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `resolveBillForTransaction - valid card calls findOrCreate`() {
        val account = creditCardAccount()
        val txDate = LocalDate.of(2024, 2, 15) // Closing day 10 -> Next month (Mar 10)
        // Due day 20 -> Mar 20

        val expectedBillId = UUID.randomUUID()
        val bill = mock<CreditCardBill>()
        whenever(bill.id).thenReturn(expectedBillId)

        whenever(repository.findOrCreate(any(), any(), any(), any())).thenReturn(bill)

        val result =
            service.resolveBillForTransaction(
                account,
                txDate,
                TransactionType.CARD,
            )

        assertThat(result).isEqualTo(expectedBillId)

        verify(repository).findOrCreate(
            financialAccountId = financialAccountId,
            accountId = accountId,
            closingDate = LocalDate.of(2024, 3, 10),
            dueDate = LocalDate.of(2024, 3, 20),
        )
    }

    // -------------------------------------------------------------------------
    // recomputeBill
    // -------------------------------------------------------------------------

    @Test
    fun `recomputeBill - null billId is no-op`() {
        service.recomputeBill(null, accountId)
        verify(repository, never()).recomputeTotal(any(), any())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun creditCardAccount(): FinancialAccount {
        return FinancialAccount(
            id = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            type = FinancialAccountType.CREDIT,
            subtype = FinancialAccountSubtype.CREDIT_CARD,
            name = "My Card",
            currencyCode = "BRL",
            cardData =
                CardData(
                    closingDay = 10,
                    dueDay = 20,
                    creditLimit = 1000.toBigDecimal(),
                ),
            createdAt = now,
            updatedAt = now,
        )
    }
}
