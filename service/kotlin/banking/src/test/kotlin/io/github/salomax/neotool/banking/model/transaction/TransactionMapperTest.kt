package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("TransactionMapper")
class TransactionMapperTest {
    private val accountId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val createdByUserId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    @Test
    fun `toDomain maps all fields correctly including Short to Int for installments`() {
        val entity =
            TransactionEntity(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = createdByUserId,
                creditDebitType = CreditDebitType.DEBIT,
                type = TransactionType.OTHER_EXPENSE,
                status = TransactionStatus.POSTED,
                source = TransactionSource.MANUAL,
                amount = BigDecimal("100.50"),
                currencyCode = "BRL",
                amountBrl = null,
                date = today,
                transactionDatetime = now,
                description = "Coffee",
                descriptionRaw = "CAFE PAGO",
                notes = "Note",
                categoryId = UUID.randomUUID(),
                counterpartyName = "Store",
                counterpartyDocument = "123",
                counterpartyBankCode = "001",
                counterpartyAccountNumber = "12345",
                paymentReason = null,
                transferPeerTransactionId = null,
                billId = null,
                installmentNumber = 2.toShort(),
                totalInstallments = 3.toShort(),
                payeeMcc = 5411,
                cardIdentifier = "****1234",
                billPostDate = null,
                providerTransactionId = "ofb-1",
                providerAccountId = "acc-1",
                appliedRuleIds = """["${UUID.randomUUID()}"]""",
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
            )

        val domain = TransactionMapper.toDomain(entity)

        assertThat(domain.id).isEqualTo(entity.id)
        assertThat(domain.financialAccountId).isEqualTo(financialAccountId)
        assertThat(domain.accountId).isEqualTo(accountId)
        assertThat(domain.createdByUserId).isEqualTo(createdByUserId)
        assertThat(domain.belongsToUserId).isEqualTo(createdByUserId)
        assertThat(domain.belongsToName).isNull()
        assertThat(domain.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
        assertThat(domain.type).isEqualTo(TransactionType.OTHER_EXPENSE)
        assertThat(domain.status).isEqualTo(TransactionStatus.POSTED)
        assertThat(domain.source).isEqualTo(TransactionSource.MANUAL)
        assertThat(domain.amount).isEqualByComparingTo("100.50")
        assertThat(domain.currencyCode).isEqualTo("BRL")
        assertThat(domain.date).isEqualTo(today)
        assertThat(domain.description).isEqualTo("Coffee")
        assertThat(domain.descriptionRaw).isEqualTo("CAFE PAGO")
        assertThat(domain.notes).isEqualTo("Note")
        assertThat(domain.categoryId).isEqualTo(entity.categoryId)
        assertThat(domain.installmentNumber).isEqualTo(2)
        assertThat(domain.totalInstallments).isEqualTo(3)
        assertThat(domain.payeeMcc).isEqualTo(5411)
        assertThat(domain.cardIdentifier).isEqualTo("****1234")
        assertThat(domain.providerTransactionId).isEqualTo("ofb-1")
        assertThat(domain.providerAccountId).isEqualTo("acc-1")
        assertThat(domain.appliedRuleIds).hasSize(1)
        assertThat(domain.createdAt).isEqualTo(now)
        assertThat(domain.updatedAt).isEqualTo(now)
    }

    @Test
    fun `toEntity sets id to null and converts Int to Short for installments`() {
        val domain =
            Transaction(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = createdByUserId,
                creditDebitType = CreditDebitType.CREDIT,
                type = TransactionType.OTHER_INCOME,
                status = TransactionStatus.POSTED,
                source = TransactionSource.MANUAL,
                amount = BigDecimal("50.00"),
                currencyCode = "BRL",
                date = today,
                description = "Refund",
                notes = null,
                categoryId = null,
                installmentNumber = 1,
                totalInstallments = 12,
                appliedRuleIds = listOf(UUID.randomUUID()),
                createdAt = now,
                updatedAt = now,
            )

        val entity = TransactionMapper.toEntity(domain)

        assertThat(entity.id).isNull()
        assertThat(entity.financialAccountId).isEqualTo(domain.financialAccountId)
        assertThat(entity.accountId).isEqualTo(domain.accountId)
        assertThat(entity.belongsToUserId).isEqualTo(createdByUserId)
        assertThat(entity.belongsToName).isNull()
        assertThat(entity.creditDebitType).isEqualTo(domain.creditDebitType)
        assertThat(entity.type).isEqualTo(TransactionType.OTHER_INCOME)
        assertThat(entity.amount).isEqualByComparingTo(domain.amount)
        assertThat(entity.date).isEqualTo(domain.date)
        assertThat(entity.description).isEqualTo(domain.description)
        assertThat(entity.installmentNumber).isEqualTo(1.toShort())
        assertThat(entity.totalInstallments).isEqualTo(12.toShort())
        assertThat(entity.appliedRuleIds).isNotBlank()
        assertThat(entity.createdAt).isEqualTo(now)
        assertThat(entity.updatedAt).isEqualTo(now)
    }

    @Test
    fun `updateEntity updates only mutable fields not source financialAccountId accountId createdByUserId`() {
        val originalSource = TransactionSource.OPEN_FINANCE
        val originalFinancialAccountId = UUID.randomUUID()
        val originalAccountId = UUID.randomUUID()
        val originalCreatedByUserId = UUID.randomUUID()

        val entity =
            TransactionEntity(
                id = UUID.randomUUID(),
                financialAccountId = originalFinancialAccountId,
                accountId = originalAccountId,
                createdByUserId = originalCreatedByUserId,
                creditDebitType = CreditDebitType.DEBIT,
                type = TransactionType.OTHER_EXPENSE,
                status = TransactionStatus.POSTED,
                source = originalSource,
                amount = BigDecimal("10.00"),
                currencyCode = "BRL",
                date = today,
                description = "Old",
                notes = "Old notes",
                categoryId = null,
                createdAt = now,
                updatedAt = now,
            )

        val otherFinancialAccountId = UUID.randomUUID()
        val otherAccountId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val updatedNow = now.plusSeconds(60)

        val domain =
            Transaction(
                id = entity.id,
                financialAccountId = otherFinancialAccountId,
                accountId = otherAccountId,
                createdByUserId = otherUserId,
                creditDebitType = CreditDebitType.CREDIT,
                type = TransactionType.OTHER_INCOME,
                status = TransactionStatus.PENDING,
                source = TransactionSource.MANUAL,
                amount = BigDecimal("99.99"),
                currencyCode = "USD",
                amountBrl = BigDecimal("500.00"),
                date = today.plusDays(1),
                transactionDatetime = updatedNow,
                description = "New",
                notes = "New notes",
                categoryId = UUID.randomUUID(),
                counterpartyName = "Other",
                createdAt = now,
                updatedAt = updatedNow,
            )

        TransactionMapper.updateEntity(entity, domain)

        assertThat(entity.source).isEqualTo(originalSource)
        assertThat(entity.financialAccountId).isEqualTo(originalFinancialAccountId)
        assertThat(entity.accountId).isEqualTo(originalAccountId)
        assertThat(entity.createdByUserId).isEqualTo(originalCreatedByUserId)
        assertThat(entity.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
        assertThat(entity.type).isEqualTo(TransactionType.OTHER_INCOME)
        assertThat(entity.status).isEqualTo(TransactionStatus.PENDING)
        assertThat(entity.amount).isEqualByComparingTo("99.99")
        assertThat(entity.currencyCode).isEqualTo("USD")
        assertThat(entity.amountBrl).isEqualByComparingTo("500.00")
        assertThat(entity.date).isEqualTo(today.plusDays(1))
        assertThat(entity.transactionDatetime).isEqualTo(updatedNow)
        assertThat(entity.description).isEqualTo("New")
        assertThat(entity.notes).isEqualTo("New notes")
        assertThat(entity.categoryId).isEqualTo(domain.categoryId)
        assertThat(entity.counterpartyName).isEqualTo("Other")
        assertThat(entity.updatedAt).isEqualTo(updatedNow)
    }
}
