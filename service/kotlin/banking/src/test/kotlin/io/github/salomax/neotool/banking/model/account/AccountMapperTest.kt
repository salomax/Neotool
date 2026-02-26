package io.github.salomax.neotool.banking.model.account

import io.github.salomax.neotool.banking.domain.account.BankData
import io.github.salomax.neotool.banking.domain.account.CardData
import io.github.salomax.neotool.banking.domain.account.ConnectionStatus
import io.github.salomax.neotool.banking.domain.account.CreditCardLevel
import io.github.salomax.neotool.banking.domain.account.CreditCardNetwork
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("AccountMapper")
class AccountMapperTest {

    private val accountId = UUID.randomUUID()
    private val createdByUserId = UUID.randomUUID()
    private val now = Instant.now()

    @Test
    fun `toDomain then toEntity preserves base fields for bank account`() {
        val entity = AccountEntity(
            id = UUID.randomUUID(),
            accountId = accountId,
            createdByUserId = createdByUserId,
            type = FinancialAccountType.BANK,
            subtype = FinancialAccountSubtype.CONTA_CORRENTE,
            name = "Checking",
            currencyCode = "BRL",
            includeInNetWorth = true,
            includeInDashboard = true,
            themeColor = "#0066cc",
            logoKey = "bank-1",
            issuerName = "Bank",
            issuerCountry = "BR",
            issuerCompeCode = "001",
            issuerIspb = "12345678",
            connectionStatus = ConnectionStatus.CONNECTED,
            consentExpiresAt = now.plusSeconds(3600),
            lastSyncAt = now,
            providerItemId = "item-1",
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
            bankData = null,
            cardData = null,
        )
        val bankDataEntity = AccountBankDataEntity(
            accountId = entity.id!!,
            account = entity,
            branchCode = "1234",
            number = "56789",
            checkDigit = "0",
            maskedNumber = "****56789",
        )
        entity.bankData = bankDataEntity

        val domain = AccountMapper.toDomain(entity)
        assertThat(domain.id).isEqualTo(entity.id)
        assertThat(domain.accountId).isEqualTo(accountId)
        assertThat(domain.type).isEqualTo(FinancialAccountType.BANK)
        assertThat(domain.subtype).isEqualTo(FinancialAccountSubtype.CONTA_CORRENTE)
        assertThat(domain.name).isEqualTo("Checking")
        assertThat(domain.bankData).isNotNull
        assertThat(domain.bankData!!.branchCode).isEqualTo("1234")
        assertThat(domain.bankData!!.number).isEqualTo("56789")
        assertThat(domain.bankData!!.maskedNumber).isEqualTo("****56789")
        assertThat(domain.cardData).isNull()

        val backEntity = AccountMapper.toEntity(domain)
        assertThat(backEntity.id).isNull()
        assertThat(backEntity.accountId).isEqualTo(domain.accountId)
        assertThat(backEntity.type).isEqualTo(domain.type)
        assertThat(backEntity.name).isEqualTo(domain.name)
        assertThat(backEntity.bankData).isNull()
    }

    @Test
    fun `toDomain then toEntity preserves base and card data for credit account`() {
        val entity = AccountEntity(
            id = UUID.randomUUID(),
            accountId = accountId,
            createdByUserId = createdByUserId,
            type = FinancialAccountType.CREDIT,
            subtype = FinancialAccountSubtype.CREDIT_CARD,
            name = "Credit Card",
            currencyCode = "BRL",
            includeInNetWorth = true,
            includeInDashboard = true,
            themeColor = null,
            logoKey = null,
            issuerName = "Issuer",
            issuerCountry = "BR",
            issuerCompeCode = null,
            issuerIspb = null,
            connectionStatus = null,
            consentExpiresAt = null,
            lastSyncAt = null,
            providerItemId = null,
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
            bankData = null,
            cardData = null,
        )
        val cardDataEntity = AccountCardDataEntity(
            accountId = entity.id!!,
            account = entity,
            maskedNumber = "****1234",
            bin = "123456",
            cardNetwork = CreditCardNetwork.VISA,
            cardLevel = CreditCardLevel.GOLD,
            creditLimit = BigDecimal("10000.00"),
            availableCreditLimit = BigDecimal("5000.00"),
            minPaymentPercentage = BigDecimal("15.00"),
            closingDay = 15,
            dueDay = 22,
        )
        entity.cardData = cardDataEntity

        val domain = AccountMapper.toDomain(entity)
        assertThat(domain.type).isEqualTo(FinancialAccountType.CREDIT)
        assertThat(domain.cardData).isNotNull
        assertThat(domain.cardData!!.creditLimit).isEqualByComparingTo("10000.00")
        assertThat(domain.cardData!!.closingDay).isEqualTo(15)
        assertThat(domain.cardData!!.dueDay).isEqualTo(22)
        assertThat(domain.cardData!!.cardNetwork).isEqualTo(CreditCardNetwork.VISA)
        assertThat(domain.cardData!!.minimumPaymentPercentage).isEqualByComparingTo("15.00")

        val backEntity = AccountMapper.toEntity(domain)
        assertThat(backEntity.cardData).isNull()
        val cardEntity = AccountMapper.toCardDataEntity(domain.cardData!!, entity)
        assertThat(cardEntity.creditLimit).isEqualByComparingTo(domain.cardData!!.creditLimit)
        assertThat(cardEntity.closingDay).isEqualTo(domain.cardData!!.closingDay)
        assertThat(cardEntity.dueDay).isEqualTo(domain.cardData!!.dueDay)
    }

    @Test
    fun `toBankDataEntity and toCardDataEntity map domain to entity`() {
        val accountEntity = AccountEntity(
            id = UUID.randomUUID(),
            accountId = accountId,
            createdByUserId = createdByUserId,
            type = FinancialAccountType.BANK,
            subtype = FinancialAccountSubtype.CONTA_CORRENTE,
            name = "Acc",
            currencyCode = "BRL",
            createdAt = now,
            updatedAt = now,
        )
        val bankData = BankData(branchCode = "1", number = "2", checkDigit = "3", maskedNumber = "****2")
        val bankEntity = AccountMapper.toBankDataEntity(bankData, accountEntity)
        assertThat(bankEntity.accountId).isEqualTo(accountEntity.id)
        assertThat(bankEntity.branchCode).isEqualTo("1")
        assertThat(bankEntity.maskedNumber).isEqualTo("****2")

        val cardData = CardData(
            creditLimit = BigDecimal("5000"),
            closingDay = 10,
            dueDay = 20,
        )
        val cardEntity = AccountMapper.toCardDataEntity(cardData, accountEntity)
        assertThat(cardEntity.creditLimit).isEqualByComparingTo("5000")
        assertThat(cardEntity.closingDay).isEqualTo(10)
        assertThat(cardEntity.dueDay).isEqualTo(20)
    }
}
