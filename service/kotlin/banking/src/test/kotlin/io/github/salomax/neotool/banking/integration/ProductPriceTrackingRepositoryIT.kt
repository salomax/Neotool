package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.import.nfce.NfceDocument
import io.github.salomax.neotool.banking.domain.import.nfce.PurchaseItem
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.repo.import.nfce.NfceDocumentRepository
import io.github.salomax.neotool.banking.repo.import.nfce.PurchaseItemRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Product Price Tracking Repository IT")
class ProductPriceTrackingRepositoryIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var nfceDocumentRepository: NfceDocumentRepository

    @Inject
    lateinit var purchaseItemRepository: PurchaseItemRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID

    @BeforeEach
    fun insertFinancialAccount() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            userId = UUID.randomUUID()
            accountId = UUID.randomUUID()
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'IT Product Tracking', 'BRL', NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `nfce document save and purchase item saveAll persist and can be loaded`() {
        val now = Instant.now()
        val transaction =
            transactionRepository.save(
                Transaction(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    status = TransactionStatus.POSTED,
                    source = TransactionSource.MANUAL,
                    amount = BigDecimal("42.50"),
                    date = LocalDate.of(2026, 3, 31),
                    transactionDatetime = now,
                    description = "Mercado IT",
                    counterpartyName = "Mercado IT",
                    counterpartyDocument = "12345678000190",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        val transactionId = requireNotNull(transaction.id)

        val savedDocument =
            nfceDocumentRepository.save(
                NfceDocument(
                    transactionId = transactionId,
                    accountId = accountId,
                    accessKey = "35260312345678000190550010000012341123456789",
                    issuerCnpj = "12345678000190",
                    issuerName = "Mercado IT",
                    emissionDate = now,
                    totalValue = BigDecimal("42.50"),
                    totalTax = BigDecimal("5.10"),
                    buyerCpf = "32167209851",
                    municipalityCode = "3550308",
                    municipality = "Sao Paulo",
                    state = "SP",
                    countryCode = "BR",
                    sourceData = mapOf("url" to "https://sefaz.example/nfce"),
                    createdAt = now,
                ),
            )
        val savedItems =
            purchaseItemRepository.saveAll(
                listOf(
                    PurchaseItem(
                        transactionId = transactionId,
                        accountId = accountId,
                        sequence = 1,
                        rawDescription = "ARROZ T1 5KG",
                        ncmCode = "10063021",
                        ean = "7891234567890",
                        issuerProductCode = "A1",
                        quantity = BigDecimal.ONE,
                        unit = "UN",
                        unitPrice = BigDecimal("25.00"),
                        totalPrice = BigDecimal("25.00"),
                        effectivePrice = BigDecimal("25.00"),
                        taxAmount = BigDecimal("3.10"),
                        createdAt = now,
                    ),
                    PurchaseItem(
                        transactionId = transactionId,
                        accountId = accountId,
                        sequence = 2,
                        rawDescription = "FEIJAO PRETO 1KG",
                        ncmCode = "07133319",
                        quantity = BigDecimal.ONE,
                        unit = "UN",
                        unitPrice = BigDecimal("17.50"),
                        totalPrice = BigDecimal("17.50"),
                        discount = BigDecimal("1.00"),
                        effectivePrice = BigDecimal("16.50"),
                        createdAt = now,
                    ),
                ),
            )

        assertThat(savedDocument.id).isNotNull()
        assertThat(savedItems).hasSize(2)
        assertThat(savedItems.all { it.id != null }).isTrue()

        val foundDocument =
            nfceDocumentRepository.findByAccessKeyAndAccountId(
                accessKey = "35260312345678000190550010000012341123456789",
                accountId = accountId,
            )
        val foundItems = purchaseItemRepository.findByTransactionId(transactionId, accountId)

        assertThat(foundDocument).isNotNull()
        assertThat(foundDocument!!.transactionId).isEqualTo(transactionId)
        assertThat(foundDocument.municipalityCode).isEqualTo("3550308")
        assertThat(foundItems).hasSize(2)
        assertThat(foundItems.map { it.sequence }).containsExactly(1, 2)
        assertThat(foundItems[1].effectivePrice).isEqualByComparingTo(BigDecimal("16.50"))
    }
}
