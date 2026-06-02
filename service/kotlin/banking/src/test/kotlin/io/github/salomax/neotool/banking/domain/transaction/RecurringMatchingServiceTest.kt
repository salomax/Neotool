package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RecurringMatchingServiceTest {
    private val recurringPaymentRepository: RecurringPaymentRepository = mock()
    private val accountId: UUID = UUID.randomUUID()
    private val now: Instant = Instant.now()

    @Test
    fun `descriptionScore normalizes accents`() {
        val service = matchingService()

        val score = service.descriptionScore("Comgas", "Transferência comgás")

        assertThat(score).isEqualTo(1.0f)
    }

    @Test
    fun `descriptionScore handles punctuation and casing`() {
        val service = matchingService()

        val score = service.descriptionScore("Netflix", "NETFLIX.COM *BR")

        assertThat(score).isEqualTo(1.0f)
    }

    @Test
    fun `descriptionScore returns zero when no token overlap`() {
        val service = matchingService()

        val score = service.descriptionScore("Conta de Luz", "ENEL SP PAGAMENTO")

        assertThat(score).isEqualTo(0.0f)
    }

    @Test
    fun `findBestMatch returns null when only description signal matches and merchant does not`() {
        val txDate = LocalDate.now()
        val transaction =
            postedTransaction(
                date = txDate,
                description = "Netflix.com",
                merchantId = null,
                amount = BigDecimal("200.00"),
            )
        val candidate =
            recurringTemplate(
                name = "Netflix",
                merchantId = UUID.randomUUID(),
                expectedAmount = BigDecimal.ZERO,
                nextDueDate = txDate,
            )
        whenever(recurringPaymentRepository.findAllByAccountId(accountId)).thenReturn(listOf(candidate))

        val matched = matchingService().findBestMatch(transaction)

        assertThat(matched).isNull()
    }

    @Test
    fun `findBestMatch returns template at exact threshold with description plus merchant`() {
        val txDate = LocalDate.now()
        val merchantId = UUID.randomUUID()
        val transaction =
            postedTransaction(
                date = txDate,
                description = "Assinatura Netflix",
                merchantId = merchantId,
                amount = BigDecimal("500.00"),
            )
        val candidate =
            recurringTemplate(
                name = "Netflix",
                merchantId = merchantId,
                expectedAmount = BigDecimal.ZERO,
                nextDueDate = txDate,
            )
        whenever(recurringPaymentRepository.findAllByAccountId(accountId)).thenReturn(listOf(candidate))

        val matched = matchingService().findBestMatch(transaction)

        assertThat(matched?.id).isEqualTo(candidate.id)
    }

    @Test
    fun `findBestMatch returns null when expectedAmount is null and only description matches`() {
        val txDate = LocalDate.now()
        val transaction =
            postedTransaction(
                date = txDate,
                description = "Internet fibra",
                merchantId = null,
                amount = BigDecimal("999.00"),
            )
        val candidate =
            recurringTemplate(
                name = "Internet",
                merchantId = UUID.randomUUID(),
                expectedAmount = null,
                nextDueDate = txDate,
            )
        whenever(recurringPaymentRepository.findAllByAccountId(accountId)).thenReturn(listOf(candidate))

        val matched = matchingService().findBestMatch(transaction)

        assertThat(matched).isNull()
    }

    @Test
    fun `findBestMatch bypasses date gate for past due template`() {
        val today = LocalDate.now()
        val merchantId = UUID.randomUUID()
        val transaction =
            postedTransaction(
                date = today,
                description = "Conta de internet",
                merchantId = merchantId,
                amount = BigDecimal("80.00"),
            )
        val candidate =
            recurringTemplate(
                name = "Internet",
                merchantId = merchantId,
                expectedAmount = BigDecimal.ZERO,
                nextDueDate = today.minusDays(31),
            )
        whenever(recurringPaymentRepository.findAllByAccountId(accountId)).thenReturn(listOf(candidate))

        val matched = matchingService(threshold = 0.55f).findBestMatch(transaction)

        assertThat(matched?.id).isEqualTo(candidate.id)
    }

    @Test
    fun `findBestMatch returns null when payment date is before past due nextDueDate`() {
        val today = LocalDate.now()
        val merchantId = UUID.randomUUID()
        val transaction =
            postedTransaction(
                date = today.minusDays(5),
                description = "Conta de internet",
                merchantId = merchantId,
                amount = BigDecimal("80.00"),
            )
        val candidate =
            recurringTemplate(
                name = "Internet",
                merchantId = merchantId,
                expectedAmount = BigDecimal.ZERO,
                nextDueDate = today.minusDays(3),
            )
        whenever(recurringPaymentRepository.findAllByAccountId(accountId)).thenReturn(listOf(candidate))

        val matched = matchingService(threshold = 0.55f).findBestMatch(transaction)

        assertThat(matched).isNull()
    }

    @Test
    fun `findBestMatch uses nearest nextDueDate as tie breaker`() {
        val txDate = LocalDate.now()
        val merchantId = UUID.randomUUID()
        val transaction =
            postedTransaction(
                date = txDate,
                description = "Academia",
                merchantId = merchantId,
                amount = BigDecimal("120.00"),
            )
        val farther =
            recurringTemplate(
                name = "Academia",
                merchantId = merchantId,
                expectedAmount = BigDecimal.ZERO,
                nextDueDate = txDate.plusDays(4),
            )
        val nearest =
            recurringTemplate(
                name = "Academia",
                merchantId = merchantId,
                expectedAmount = BigDecimal.ZERO,
                nextDueDate = txDate.plusDays(1),
            )
        whenever(recurringPaymentRepository.findAllByAccountId(accountId)).thenReturn(listOf(farther, nearest))

        val matched = matchingService().findBestMatch(transaction)

        assertThat(matched?.id).isEqualTo(nearest.id)
    }

    private fun matchingService(
        threshold: Float = 0.80f,
        dateGateDays: Long = 10,
    ): RecurringMatchingService =
        RecurringMatchingService(
            recurringPaymentRepository = recurringPaymentRepository,
            threshold = threshold,
            dateGateDays = dateGateDays,
        )

    private fun recurringTemplate(
        name: String,
        merchantId: UUID?,
        expectedAmount: BigDecimal?,
        nextDueDate: LocalDate,
    ): RecurringPayment =
        RecurringPayment(
            id = UUID.randomUUID(),
            accountId = accountId,
            financialAccountId = null,
            name = name,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            expectedAmount = expectedAmount,
            currencyCode = "BRL",
            categoryId = null,
            merchantId = merchantId,
            frequency = RecurringFrequency.MONTHLY,
            dayOfMonth = 10,
            intervalDays = null,
            remindDaysBefore = 3,
            status = RecurringPaymentStatus.ACTIVE,
            nextDueDate = nextDueDate,
            lastPaidDate = null,
            notes = null,
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
        )

    private fun postedTransaction(
        date: LocalDate,
        description: String,
        merchantId: UUID?,
        amount: BigDecimal,
    ): Transaction =
        Transaction(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = accountId,
            createdByUserId = UUID.randomUUID(),
            belongsToUserId = UUID.randomUUID(),
            belongsToName = null,
            creditDebitType = CreditDebitType.DEBIT,
            type = TransactionType.OTHER_EXPENSE,
            status = TransactionStatus.POSTED,
            source = TransactionSource.MANUAL,
            amount = amount,
            currencyCode = "BRL",
            amountBrl = null,
            date = date,
            transactionDatetime = null,
            description = description,
            descriptionRaw = null,
            notes = null,
            tags = emptyList(),
            categoryId = null,
            categorySource = null,
            merchantId = merchantId,
            counterpartyName = null,
            counterpartyDocument = null,
            counterpartyBankCode = null,
            counterpartyAccountNumber = null,
            paymentReason = null,
            transferPeerTransactionId = null,
            investmentTransactionId = null,
            recurringPaymentId = null,
            billId = null,
            installmentNumber = null,
            totalInstallments = null,
            payeeMcc = null,
            cardIdentifier = null,
            billPostDate = null,
            providerTransactionId = null,
            providerAccountId = null,
            importFingerprint = null,
            importJobId = null,
            sourceData = null,
            appliedRuleHistory = emptyList(),
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
        )
}
