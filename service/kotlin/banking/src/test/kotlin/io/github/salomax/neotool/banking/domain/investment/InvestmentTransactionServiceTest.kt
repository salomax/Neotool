package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionRepository
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.model.dto.investment.ManualFundValuation
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.model.dto.investment.UpdateInvestmentTransactionCommand
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.attachment.HoldingAttachmentRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryEntityRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.service.investment.PortfolioFlowEventService
import io.github.salomax.neotool.banking.service.investment.lifecycle.CouponPendingActionHandler
import io.github.salomax.neotool.banking.service.investment.lifecycle.CustodianCashLedgerHandler
import io.github.salomax.neotool.banking.service.investment.lifecycle.InvestmentTransactionLifecycleOrchestrator
import io.github.salomax.neotool.banking.service.investment.lifecycle.MaturityPendingActionHandler
import io.github.salomax.neotool.banking.service.investment.lifecycle.PortfolioFlowEventHandler
import io.github.salomax.neotool.banking.service.investment.recalculation.HoldingPositionEvolution
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@DisplayName("InvestmentTransactionService")
class InvestmentTransactionServiceTest {
    private lateinit var investmentTransactionRepository: InvestmentTransactionRepository
    private lateinit var holdingRepository: HoldingRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var financialAccountRepository: FinancialAccountRepository
    private lateinit var pendingActionRepository: AccountPendingActionRepository
    private lateinit var transactionService: TransactionService
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var transactionCategoryEntityRepository: TransactionCategoryEntityRepository
    private lateinit var portfolioFlowEventService: PortfolioFlowEventService
    private lateinit var holdingAttachmentRepository: HoldingAttachmentRepository
    private lateinit var service: InvestmentTransactionService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()
    private val holdingId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private val custodyAccount =
        FinancialAccount(
            id = financialAccountId,
            accountId = accountId,
            createdByUserId = userId,
            type = FinancialAccountType.INVESTMENT,
            subtype = FinancialAccountSubtype.BROKERAGE,
            name = "XP Investimentos",
            currencyCode = "BRL",
            createdAt = now,
            updatedAt = now,
        )

    private val asset =
        Asset(
            id = assetId,
            ticker = "PETR4",
            name = "Petrobras ON",
            assetType = AssetType.STOCK,
            createdAt = now,
            updatedAt = now,
        )

    private fun fundAsset(
        type: AssetType = AssetType.MUTUAL_FUND,
        fundCnpj: String? = "12345678000199",
    ) = Asset(
        id = assetId,
        name = "Fundo XP",
        assetType = type,
        fundCnpj = fundCnpj,
        createdAt = now,
        updatedAt = now,
    )

    /** An empty holding (quantity = 0, no existing position) */
    private fun emptyHolding() =
        Holding(
            id = holdingId,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            quantity = ZERO,
            averageCost = ZERO,
            source = InvestmentSource.MANUAL,
            createdAt = now,
            updatedAt = now,
        )

    /** A holding with an existing position */
    private fun holdingWith(
        quantity: String,
        averageCost: String,
    ) = emptyHolding().copy(
        quantity = BigDecimal(quantity),
        averageCost = BigDecimal(averageCost),
    )

    private fun savedHolding(holding: Holding) = holding.copy(id = holdingId)

    private fun savedTransaction(tx: InvestmentTransaction) = tx.copy(id = UUID.randomUUID())

    /** Minimal buy command */
    private fun buyCommand(
        quantity: String,
        unitPrice: String,
        grossValue: String = (BigDecimal(quantity) * BigDecimal(unitPrice)).toPlainString(),
    ) = RecordInvestmentTransactionCommand(
        financialAccountId = financialAccountId,
        assetId = assetId,
        direction = InvestmentTransactionDirection.INFLOW,
        type = InvestmentTransactionType.PURCHASE,
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal(unitPrice),
        grossValue = BigDecimal(grossValue),
        tradeDate = today,
    )

    @BeforeEach
    fun setUp() {
        investmentTransactionRepository = mock()
        holdingRepository = mock()
        assetRepository = mock()
        financialAccountRepository = mock()
        pendingActionRepository = mock()
        transactionService = mock()
        transactionRepository = mock()
        transactionCategoryEntityRepository = mock()
        portfolioFlowEventService = mock()
        holdingAttachmentRepository = mock()
        val lifecycleOrchestrator =
            InvestmentTransactionLifecycleOrchestrator(
                listOf(
                    CustodianCashLedgerHandler(
                        transactionService = transactionService,
                        transactionRepository = transactionRepository,
                        transactionCategoryEntityRepository = transactionCategoryEntityRepository,
                        portfolioFlowEventService = portfolioFlowEventService,
                    ),
                    PortfolioFlowEventHandler(portfolioFlowEventService),
                    MaturityPendingActionHandler(pendingActionRepository),
                    CouponPendingActionHandler(
                        pendingActionRepository = pendingActionRepository,
                        investmentTransactionRepository = investmentTransactionRepository,
                        couponScheduleService = CouponScheduleService(),
                        assetRepository = assetRepository,
                    ),
                ),
            )
        service =
            InvestmentTransactionService(
                investmentTransactionRepository,
                holdingRepository,
                assetRepository,
                financialAccountRepository,
                pendingActionRepository,
                portfolioFlowEventService,
                holdingAttachmentRepository,
                lifecycleOrchestrator,
            )

        // Default stubs used by most tests
        whenever(financialAccountRepository.findByAccountId(accountId))
            .thenReturn(listOf(custodyAccount))
        whenever(financialAccountRepository.existsByIdAndAccountId(financialAccountId, accountId))
            .thenReturn(true)
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(investmentTransactionRepository.save(any())).thenAnswer {
            savedTransaction(it.arguments[0] as InvestmentTransaction)
        }
        whenever(transactionRepository.existsByInvestmentTransactionId(any())).thenReturn(false)
        whenever(transactionRepository.findByInvestmentTransactionIds(any(), any())).thenReturn(emptyList())
        whenever(transactionService.delete(any(), any())).thenReturn(true)
        whenever(portfolioFlowEventService.deleteEventsByRelatedHolding(any(), any())).thenReturn(0)
        whenever(holdingAttachmentRepository.deleteByHoldingIdAndAccountId(any(), any())).thenReturn(0)
        whenever(pendingActionRepository.cancelByEntityAndType(any(), any(), any(), any())).thenReturn(false)
        whenever(pendingActionRepository.findPendingByEntityAndType(any(), any(), any())).thenReturn(null)
        whenever(transactionCategoryEntityRepository.findSystemSubcategoryId(any(), any()))
            .thenReturn(UUID.randomUUID())
        whenever(transactionService.create(any(), any(), any())).thenReturn(mock<Transaction>())
        whenever(
            portfolioFlowEventService.recordEvent(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenAnswer { inv ->
            val fa = inv.getArgument<UUID>(0)
            val acc = inv.getArgument<UUID>(1)
            val eventDate = inv.getArgument<LocalDate>(2)
            val eventType = inv.getArgument<PortfolioFlowEventType>(3)
            val grossAmount = inv.getArgument<BigDecimal>(4)
            val relatedHoldingId = inv.getArgument<UUID?>(5)
            val sourceReferenceId = inv.getArgument<UUID?>(6)
            val sourceReferenceType = inv.getArgument<String?>(7)
            val triggeredBy =
                if (sourceReferenceId != null && sourceReferenceType != null) {
                    "$sourceReferenceType:$sourceReferenceId"
                } else {
                    eventType.name.lowercase()
                }
            portfolioFlowEventService.requestRecalculation(fa, acc, eventDate, triggeredBy)
            InvestmentPortfolioFlowEvent(
                financialAccountId = fa,
                accountId = acc,
                eventDate = eventDate,
                eventType = eventType,
                grossAmount = grossAmount,
                portfolioValueBefore = BigDecimal.ZERO,
                quotaValueAtEvent = BigDecimal.ONE,
                quotasTransacted = BigDecimal.ZERO,
                relatedHoldingId = relatedHoldingId,
                sourceReferenceId = sourceReferenceId,
                sourceReferenceType = sourceReferenceType,
            )
        }
    }

    @Test
    fun `first RF purchase derives pre fixed rate and defaults issue date to trade date`() {
        val fixedIncomeAsset =
            asset.copy(
                ticker = null,
                name = "CDB IPCA+",
                assetType = AssetType.CDB,
            )
        val command =
            RecordInvestmentTransactionCommand(
                financialAccountId = financialAccountId,
                assetId = assetId,
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("10674.30"),
                unitPrice = BigDecimal.ONE,
                grossValue = BigDecimal("10674.30"),
                tradeDate = LocalDate.of(2024, 9, 23),
                indexer = Indexer.IPCA,
                fixedAnnualRate = BigDecimal("0.088"),
            )

        whenever(assetRepository.findById(assetId)).thenReturn(fixedIncomeAsset)
        whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId)).thenReturn(null)
        whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

        service.record(command, accountId, userId)

        val holdingCaptor = argumentCaptor<Holding>()
        verify(holdingRepository).save(holdingCaptor.capture())
        assertThat(holdingCaptor.firstValue.fixedAnnualRate).isEqualByComparingTo("0.088")
        assertThat(holdingCaptor.firstValue.issueDate).isEqualTo(LocalDate.of(2024, 9, 23))
    }

    @Nested
    @DisplayName("DELETE")
    inner class Delete {
        @Test
        fun `recomputes holding from remaining manual transactions`() {
            val deletedTxId = UUID.randomUUID()
            val remainingTxId = UUID.randomUUID()
            val currentHolding = holdingWith(quantity = "150", averageCost = "29.00")
            val deletedTx =
                InvestmentTransaction(
                    id = deletedTxId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("50"),
                    unitPrice = BigDecimal("30.00"),
                    grossValue = BigDecimal("1500.00"),
                    tradeDate = today.plusDays(1),
                    source = InvestmentSource.MANUAL,
                    createdAt = now.plusSeconds(60),
                    updatedAt = now.plusSeconds(60),
                )
            val remainingTx =
                InvestmentTransaction(
                    id = remainingTxId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("28.50"),
                    grossValue = BigDecimal("2850.00"),
                    tradeDate = today,
                    source = InvestmentSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(investmentTransactionRepository.findById(deletedTxId, accountId)).thenReturn(deletedTx)
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(currentHolding)
            whenever(investmentTransactionRepository.softDelete(deletedTxId, accountId)).thenReturn(true)
            whenever(
                investmentTransactionRepository.findByHolding(holdingId, accountId),
            ).thenReturn(listOf(remainingTx))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val deleted = service.delete(deletedTxId, accountId)

            assertThat(deleted).isTrue()
            val holdingCaptor = argumentCaptor<Holding>()
            verify(holdingRepository).update(holdingCaptor.capture())
            assertThat(holdingCaptor.firstValue.quantity).isEqualByComparingTo("100")
            assertThat(holdingCaptor.firstValue.averageCost).isEqualByComparingTo("28.50")
            verify(holdingRepository, never()).softDelete(any(), any())
        }

        @Test
        fun `delete soft deletes linked custodian TRANSFER and removes BANKING portfolio flow`() {
            val deletedTxId = UUID.randomUUID()
            val bankingLegId = UUID.randomUUID()
            val currentHolding = holdingWith(quantity = "150", averageCost = "29.00")
            val deletedTx =
                InvestmentTransaction(
                    id = deletedTxId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("50"),
                    unitPrice = BigDecimal("30.00"),
                    grossValue = BigDecimal("1500.00"),
                    tradeDate = today.plusDays(1),
                    source = InvestmentSource.MANUAL,
                    createdAt = now.plusSeconds(60),
                    updatedAt = now.plusSeconds(60),
                )
            val bankingLeg =
                Transaction(
                    id = bankingLegId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.TRANSFER,
                    amount = BigDecimal("1500.00"),
                    date = today,
                    description = "Compra / aplicação",
                    investmentTransactionId = deletedTxId,
                    createdAt = now,
                    updatedAt = now,
                    status = TransactionStatus.POSTED,
                )
            val remainingTx =
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("28.50"),
                    grossValue = BigDecimal("2850.00"),
                    tradeDate = today,
                    source = InvestmentSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(investmentTransactionRepository.findById(deletedTxId, accountId)).thenReturn(deletedTx)
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(currentHolding)
            whenever(investmentTransactionRepository.softDelete(deletedTxId, accountId)).thenReturn(true)
            whenever(
                investmentTransactionRepository.findByHolding(holdingId, accountId),
            ).thenReturn(listOf(remainingTx))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }
            whenever(transactionRepository.findByInvestmentTransactionIds(any(), eq(accountId)))
                .thenReturn(listOf(bankingLeg))

            val deleted = service.delete(deletedTxId, accountId)

            assertThat(deleted).isTrue()
            verify(portfolioFlowEventService).deleteEventBySourceReference(
                bankingLegId,
                "BANKING_TRANSACTION",
            )
            verify(transactionService).delete(bankingLegId, accountId)
        }

        @Test
        fun `soft deletes holding when the deleted transaction was the last one`() {
            val deletedTxId = UUID.randomUUID()
            val currentHolding = holdingWith(quantity = "100", averageCost = "28.50")
            val deletedTx =
                InvestmentTransaction(
                    id = deletedTxId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("28.50"),
                    grossValue = BigDecimal("2850.00"),
                    tradeDate = today,
                    source = InvestmentSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(investmentTransactionRepository.findById(deletedTxId, accountId)).thenReturn(deletedTx)
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(currentHolding)
            whenever(investmentTransactionRepository.softDelete(deletedTxId, accountId)).thenReturn(true)
            whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(emptyList())

            val deleted = service.delete(deletedTxId, accountId)

            assertThat(deleted).isTrue()
            verify(holdingRepository).softDelete(holdingId, accountId)
            verify(holdingRepository, never()).update(any())
        }

        @Test
        fun `deleteHolding soft deletes all manual transactions and the holding`() {
            val txOneId = UUID.randomUUID()
            val txTwoId = UUID.randomUUID()
            val currentHolding = holdingWith(quantity = "100", averageCost = "28.50")
            val txOne =
                InvestmentTransaction(
                    id = txOneId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("80"),
                    unitPrice = BigDecimal("28.00"),
                    grossValue = BigDecimal("2240.00"),
                    tradeDate = today.minusDays(1),
                    source = InvestmentSource.MANUAL,
                    createdAt = now.minusSeconds(60),
                    updatedAt = now.minusSeconds(60),
                )
            val txTwo =
                InvestmentTransaction(
                    id = txTwoId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("20"),
                    unitPrice = BigDecimal("30.50"),
                    grossValue = BigDecimal("610.00"),
                    tradeDate = today,
                    source = InvestmentSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(currentHolding)
            whenever(
                investmentTransactionRepository.findByHolding(holdingId, accountId),
            ).thenReturn(listOf(txTwo, txOne))
            whenever(investmentTransactionRepository.softDelete(txOneId, accountId)).thenReturn(true)
            whenever(investmentTransactionRepository.softDelete(txTwoId, accountId)).thenReturn(true)
            whenever(holdingRepository.softDelete(holdingId, accountId)).thenReturn(true)

            val deleted = service.deleteHolding(holdingId, accountId)

            assertThat(deleted).isTrue()
            verify(investmentTransactionRepository).softDelete(txOneId, accountId)
            verify(investmentTransactionRepository).softDelete(txTwoId, accountId)
            verify(portfolioFlowEventService).deleteEventsByRelatedHolding(financialAccountId, holdingId)
            verify(holdingAttachmentRepository).deleteByHoldingIdAndAccountId(holdingId, accountId)
            verify(holdingRepository).softDelete(holdingId, accountId)
        }

        @Test
        fun `deleteHolding triggers recalculation from earliest transaction trade date`() {
            val oldTxId = UUID.randomUUID()
            val newTxId = UUID.randomUUID()
            val oldTx =
                InvestmentTransaction(
                    id = oldTxId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    grossValue = BigDecimal("5000.00"),
                    tradeDate = today.minusMonths(6),
                    source = InvestmentSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )
            val newTx =
                InvestmentTransaction(
                    id = newTxId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    grossValue = BigDecimal("2000.00"),
                    tradeDate = today.minusMonths(1),
                    source = InvestmentSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holdingWith("1", "5000.00"))
            whenever(
                investmentTransactionRepository.findByHolding(holdingId, accountId),
            ).thenReturn(listOf(newTx, oldTx))
            whenever(investmentTransactionRepository.softDelete(any(), any())).thenReturn(true)
            whenever(holdingRepository.softDelete(holdingId, accountId)).thenReturn(true)
            whenever(portfolioFlowEventService.requestRecalculation(any(), any(), any(), any())).thenReturn(
                io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob(
                    accountId = accountId,
                    status = io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus.PENDING,
                    recalculateFrom = today.minusMonths(6),
                    triggeredBy = "test",
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            service.deleteHolding(holdingId, accountId)

            val captor = argumentCaptor<java.time.LocalDate>()
            verify(portfolioFlowEventService).requestRecalculation(
                financialAccountId = org.mockito.kotlin.eq(financialAccountId),
                accountId = org.mockito.kotlin.eq(accountId),
                recalculateFrom = captor.capture(),
                triggeredBy = org.mockito.kotlin.any(),
            )
            assertThat(captor.firstValue).isEqualTo(today.minusMonths(6))
        }

        @Test
        fun `deleteHolding allows manual holding with system withholding tax transactions`() {
            val purchaseTxId = UUID.randomUUID()
            val withholdingTxId = UUID.randomUUID()
            val purchaseTx =
                InvestmentTransaction(
                    id = purchaseTxId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("10.00"),
                    grossValue = BigDecimal("1000.00"),
                    tradeDate = LocalDate.of(2025, 10, 22),
                    source = InvestmentSource.MANUAL,
                    createdAt = now.minusSeconds(60),
                    updatedAt = now.minusSeconds(60),
                )
            val withholdingTx =
                InvestmentTransaction(
                    id = withholdingTxId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.WITHHOLDING_TAX,
                    quantity = BigDecimal("8.12345678"),
                    unitPrice = BigDecimal("1.23456789"),
                    grossValue = BigDecimal("10.03"),
                    netValue = BigDecimal("10.03"),
                    tradeDate = LocalDate.of(2026, 5, 29),
                    source = InvestmentSource.SYSTEM,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(
                holdingWith("91.87654322", "10.00"),
            )
            whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(
                listOf(withholdingTx, purchaseTx),
            )
            whenever(investmentTransactionRepository.softDelete(purchaseTxId, accountId)).thenReturn(true)
            whenever(investmentTransactionRepository.softDelete(withholdingTxId, accountId)).thenReturn(true)
            whenever(holdingRepository.softDelete(holdingId, accountId)).thenReturn(true)
            whenever(assetRepository.findById(assetId)).thenReturn(fundAsset())

            val deleted = service.deleteHolding(holdingId, accountId)

            assertThat(deleted).isTrue()
            verify(investmentTransactionRepository).softDelete(purchaseTxId, accountId)
            verify(investmentTransactionRepository).softDelete(withholdingTxId, accountId)
            verify(holdingRepository).softDelete(holdingId, accountId)
        }

        @Test
        fun `deleteHolding with no transactions triggers recalculation from holding issueDate`() {
            // BUG FIX: holdings imported without PURCHASE transactions (e.g. via B3/XP Excel import)
            // were leaving stale quotas because no recalculation was triggered.
            val rfPreHolding =
                holdingWith("1", "3000.00").copy(
                    issueDate = LocalDate.of(2025, 1, 1),
                )
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(rfPreHolding)
            whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(emptyList())
            whenever(holdingRepository.softDelete(holdingId, accountId)).thenReturn(true)
            whenever(portfolioFlowEventService.requestRecalculation(any(), any(), any(), any())).thenReturn(
                io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob(
                    accountId = accountId,
                    status = io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus.PENDING,
                    recalculateFrom = LocalDate.of(2025, 1, 1),
                    triggeredBy = "test",
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            service.deleteHolding(holdingId, accountId)

            val captor = argumentCaptor<java.time.LocalDate>()
            verify(portfolioFlowEventService).requestRecalculation(
                financialAccountId = org.mockito.kotlin.eq(financialAccountId),
                accountId = org.mockito.kotlin.eq(accountId),
                recalculateFrom = captor.capture(),
                triggeredBy = org.mockito.kotlin.any(),
            )
            assertThat(captor.firstValue).isEqualTo(LocalDate.of(2025, 1, 1))
        }

        @Test
        fun `deleteHolding with no transactions and no issueDate triggers recalculation from today`() {
            val holdingNoIssueDate = holdingWith("1", "3000.00").copy(issueDate = null)
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holdingNoIssueDate)
            whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(emptyList())
            whenever(holdingRepository.softDelete(holdingId, accountId)).thenReturn(true)
            whenever(portfolioFlowEventService.requestRecalculation(any(), any(), any(), any())).thenReturn(
                io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob(
                    accountId = accountId,
                    status = io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus.PENDING,
                    recalculateFrom = today,
                    triggeredBy = "test",
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            service.deleteHolding(holdingId, accountId)

            val captor = argumentCaptor<java.time.LocalDate>()
            verify(portfolioFlowEventService).requestRecalculation(
                financialAccountId = org.mockito.kotlin.eq(financialAccountId),
                accountId = org.mockito.kotlin.eq(accountId),
                recalculateFrom = captor.capture(),
                triggeredBy = org.mockito.kotlin.any(),
            )
            assertThat(captor.firstValue).isEqualTo(today)
        }
    }

    // ── PURCHASE — custo médio ponderado ─────────────────────────────────────

    @Nested
    @DisplayName("PURCHASE — weighted average cost (custo médio ponderado)")
    inner class PurchaseTests {
        @Test
        fun `first buy on empty holding sets averageCost equal to unit price`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            service.record(buyCommand("100", "28.50"), accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).save(captor.capture())

            val saved = captor.firstValue
            assertThat(saved.quantity).isEqualByComparingTo("100")
            assertThat(saved.averageCost).isEqualByComparingTo("28.50")
        }

        @Test
        fun `PURCHASE records DEPOSIT portfolio flow so TWR treats new capital as a flow not as return`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            service.record(buyCommand("100", "28.50"), accountId, userId)

            verify(portfolioFlowEventService).recordEvent(
                financialAccountId = eq(financialAccountId),
                accountId = eq(accountId),
                eventDate = eq(today),
                eventType = eq(PortfolioFlowEventType.DEPOSIT),
                grossAmount = eq(BigDecimal("2850.00")),
                relatedHoldingId = eq(holdingId),
                sourceReferenceId = any(),
                sourceReferenceType = eq("INVESTMENT_TRANSACTION"),
            )
        }

        @Test
        fun `purchase writes a custodian DEBIT TRANSFER so NAV equals holdings plus cash for TWR`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            service.record(buyCommand("100", "28.50"), accountId, userId)

            val cmdCaptor = argumentCaptor<CreateTransactionCommand>()
            verify(transactionService).create(cmdCaptor.capture(), eq(accountId), eq(userId))
            val cmd = cmdCaptor.firstValue
            assertThat(cmd.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(cmd.type).isEqualTo(TransactionType.TRANSFER)
            assertThat(cmd.description).isEqualTo("Compra / aplicação")
            assertThat(cmd.investmentTransactionId).isNotNull
            assertThat(cmd.date).isEqualTo(today)
            verify(transactionCategoryEntityRepository).findSystemSubcategoryId("Investimento aplicado", "Ações")
        }

        @Test
        fun `purchase cash leg posts on settlementDate when it differs from tradeDate`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            val settlement = today.plusDays(2)
            service.record(buyCommand("100", "28.50").copy(settlementDate = settlement), accountId, userId)

            val cmdCaptor = argumentCaptor<CreateTransactionCommand>()
            verify(transactionService).create(cmdCaptor.capture(), eq(accountId), eq(userId))
            assertThat(cmdCaptor.firstValue.date).isEqualTo(settlement)
        }

        @Test
        fun `second buy at different price recalculates averageCost correctly`() {
            // Existing: 100 shares at R$28.50 → cost basis R$2,850
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "28.50"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            // Buying 100 more at R$32.00 → cost basis R$3,200
            // Expected avg = (100×28.50 + 100×32.00) / 200 = (2850+3200)/200 = 6050/200 = 30.25
            service.record(buyCommand("100", "32.00"), accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("200")
            assertThat(updated.averageCost).isEqualByComparingTo("30.25")
        }

        @Test
        fun `multiple buys at different prices produce correct weighted average`() {
            // After two buys: 100@28.50 + 100@32.00 = avg 30.25
            val existingHolding = holdingWith("200", "30.25")
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(existingHolding)
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            // Buying 50 more at R$31.00
            // Expected avg = (200×30.25 + 50×31.00) / 250 = (6050+1550)/250 = 7600/250 = 30.40
            service.record(buyCommand("50", "31.00"), accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("250")
            assertThat(updated.averageCost).isEqualByComparingTo("30.40")
        }

        @Test
        fun `SUBSCRIPTION is treated identically to PURCHASE for average cost`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "10.00"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val subscriptionCommand =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.SUBSCRIPTION,
                    quantity = BigDecimal("50"),
                    unitPrice = BigDecimal("12.00"),
                    grossValue = BigDecimal("600"),
                    tradeDate = today,
                )

            service.record(subscriptionCommand, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            // (100×10 + 50×12) / 150 = (1000+600)/150 = 1600/150 ≈ 10.6667
            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("150")
            assertThat(updated.averageCost.setScale(4, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("10.6667")
        }

        @Test
        fun `subscription cash leg description does not include asset name`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "10.00"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val subscriptionCommand =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.SUBSCRIPTION,
                    quantity = BigDecimal("50"),
                    unitPrice = BigDecimal("12.00"),
                    grossValue = BigDecimal("600"),
                    tradeDate = today,
                )

            service.record(subscriptionCommand, accountId, userId)

            val cmdCaptor = argumentCaptor<CreateTransactionCommand>()
            verify(transactionService).create(cmdCaptor.capture(), eq(accountId), eq(userId))
            assertThat(cmdCaptor.firstValue.description).isEqualTo("Subscrição")
        }
    }

    // ── SALE ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SALE")
    inner class SaleTests {
        @Test
        fun `sale reduces quantity and leaves averageCost unchanged (RFB rule)`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("200", "30.25"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.SALE,
                    quantity = BigDecimal("80"),
                    unitPrice = BigDecimal("35.00"),
                    grossValue = BigDecimal("2800"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("120")
            // averageCost must NOT change on sale
            assertThat(updated.averageCost).isEqualByComparingTo("30.25")
        }

        @Test
        fun `sale cash leg posts on settlementDate when it differs from tradeDate`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("200", "30.25"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val settlement = today.plusDays(2)
            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.SALE,
                    quantity = BigDecimal("80"),
                    unitPrice = BigDecimal("35.00"),
                    grossValue = BigDecimal("2800"),
                    tradeDate = today,
                    settlementDate = settlement,
                )

            service.record(command, accountId, userId)

            val cmdCaptor = argumentCaptor<CreateTransactionCommand>()
            verify(transactionService).create(cmdCaptor.capture(), eq(accountId), eq(userId))
            assertThat(cmdCaptor.firstValue.type).isEqualTo(TransactionType.INVESTMENT_RETURN)
            assertThat(cmdCaptor.firstValue.creditDebitType).isEqualTo(CreditDebitType.CREDIT)
            assertThat(cmdCaptor.firstValue.date).isEqualTo(settlement)
        }

        @Test
        fun `selling all shares zeroes the quantity`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "28.50"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.SALE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("35.00"),
                    grossValue = BigDecimal("3500"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            assertThat(captor.firstValue.quantity).isEqualByComparingTo(ZERO)
        }

        @Test
        fun `sale with quantity exceeding holding throws IllegalArgumentException`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("50", "28.50"))

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.SALE,
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("35.00"),
                    grossValue = BigDecimal("3500"),
                    tradeDate = today,
                )

            assertThrows<IllegalArgumentException> {
                service.record(command, accountId, userId)
            }

            verify(investmentTransactionRepository, never()).save(any())
        }
    }

    // ── MATURITY ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MATURITY")
    inner class MaturityTests {
        @Test
        fun `maturity with explicit quantity reduces position`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("1000", "1.05"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.MATURITY,
                    quantity = BigDecimal("1000"),
                    grossValue = BigDecimal("1100"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            assertThat(captor.firstValue.quantity).isEqualByComparingTo(ZERO)
        }

        @Test
        fun `maturity without quantity closes the full position`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("500", "1.02"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.MATURITY,
                    // null = full maturity
                    quantity = null,
                    grossValue = BigDecimal("600"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            assertThat(captor.firstValue.quantity).isEqualByComparingTo(ZERO)
        }
    }

    // ── CORPORATE EVENTS ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Corporate events — quantity and cost basis adjustments")
    inner class CorporateEventTests {
        @Test
        fun `STOCK_SPLIT increases quantity and reduces averageCost preserving total cost basis`() {
            // 100 shares at R$20.00 → cost basis R$2,000
            // 2-for-1 split: receive 100 additional shares
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "20.00"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.STOCK_SPLIT,
                    // additional shares received
                    quantity = BigDecimal("100"),
                    grossValue = ZERO,
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("200")
            // Total basis R$2,000 preserved → new avg = 2000/200 = 10.00
            assertThat(updated.averageCost).isEqualByComparingTo("10.00")
        }

        @Test
        fun `BONUS_SHARES dilutes averageCost with zero-cost inflow`() {
            // 100 shares at R$50.00 → cost basis R$5,000
            // 10% bonus: receive 10 bonus shares at zero cost
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "50.00"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.BONUS_SHARES,
                    quantity = BigDecimal("10"),
                    grossValue = ZERO,
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("110")
            // R$5,000 / 110 ≈ 45.4545
            assertThat(updated.averageCost.setScale(4, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo(BigDecimal("5000").divide(BigDecimal("110"), 4, java.math.RoundingMode.HALF_UP))
        }

        @Test
        fun `REVERSE_SPLIT reduces quantity and concentrates cost basis in fewer shares`() {
            // 100 shares at R$5.00 → cost basis R$500
            // 1-for-2 reverse split: remove 50 shares
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "5.00"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.REVERSE_SPLIT,
                    // shares removed
                    quantity = BigDecimal("50"),
                    grossValue = ZERO,
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("50")
            // R$500 concentrated in 50 shares → avg = 500/50 = 10.00
            assertThat(updated.averageCost).isEqualByComparingTo("10.00")
        }

        @Test
        fun `cost basis is preserved across STOCK_SPLIT and subsequent SALE`() {
            // Start: 100 shares @ R$20 → cost basis R$2,000
            val holding = holdingWith("100", "20.00")
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holding)
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            // Step 1: split → 200 shares @ R$10
            val splitCommand =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.STOCK_SPLIT,
                    quantity = BigDecimal("100"),
                    grossValue = ZERO,
                    tradeDate = today,
                )

            service.record(splitCommand, accountId, userId)

            val captor1 = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor1.capture())
            val afterSplit = captor1.firstValue
            assertThat(afterSplit.quantity).isEqualByComparingTo("200")
            assertThat(afterSplit.averageCost).isEqualByComparingTo("10.00")

            // Verify cost basis unchanged: 200 × 10 = R$2,000
            val basisAfterSplit = afterSplit.quantity * afterSplit.averageCost
            assertThat(basisAfterSplit).isEqualByComparingTo("2000")
        }
    }

    // ── INCOME EVENTS ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Income events — no holding mutation")
    inner class IncomeEventTests {
        @Test
        fun `DIVIDEND does not change quantity or averageCost`() {
            val holding = holdingWith("200", "30.25")
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holding)
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.DIVIDEND,
                    grossValue = BigDecimal("420.00"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("200")
            assertThat(updated.averageCost).isEqualByComparingTo("30.25")
        }

        @Test
        fun `INTEREST_ON_EQUITY (JCP) does not change quantity or averageCost`() {
            val holding = holdingWith("500", "10.00")
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holding)
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.INTEREST_ON_EQUITY,
                    grossValue = BigDecimal("250.00"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("500")
            assertThat(updated.averageCost).isEqualByComparingTo("10.00")
        }

        @Test
        fun `WITHHOLDING_TAX (come-cotas) reduces quantity by deducted quotas and leaves averageCost unchanged`() {
            whenever(assetRepository.findById(assetId)).thenReturn(fundAsset())
            val holding = holdingWith("1000", "1.15")
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holding)
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val deductedQuotas = BigDecimal("50")
            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.WITHHOLDING_TAX,
                    quantity = deductedQuotas,
                    grossValue = BigDecimal("50.00"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("950")
            assertThat(updated.averageCost).isEqualByComparingTo("1.15")
        }
    }

    @Nested
    @DisplayName("Manual fund valuation")
    inner class ManualFundValuationTests {
        @Test
        fun `first manual fund purchase persists pricing mode and manual valuation`() {
            whenever(assetRepository.findById(assetId)).thenReturn(fundAsset())
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            val command =
                buyCommand("100", "1.00").copy(
                    pricingMode = HoldingPricingMode.MANUAL,
                    manualValuation =
                        ManualFundValuation(
                            grossValue = BigDecimal("123.45"),
                            valuedAt = today,
                        ),
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).save(captor.capture())
            val saved = captor.firstValue
            assertThat(saved.pricingMode).isEqualTo(HoldingPricingMode.MANUAL)
            assertThat(saved.manualGrossValue).isEqualByComparingTo("123.45")
            assertThat(saved.manualValuedAt).isEqualTo(today)
        }

        @Test
        fun `manual valuation is rejected for market-data fund holdings`() {
            whenever(assetRepository.findById(assetId)).thenReturn(fundAsset())
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)

            val command =
                buyCommand("10", "1.00").copy(
                    pricingMode = HoldingPricingMode.MARKET_DATA,
                    manualValuation =
                        ManualFundValuation(
                            grossValue = BigDecimal("10.50"),
                            valuedAt = today,
                        ),
                )

            assertThrows<IllegalArgumentException> {
                service.record(command, accountId, userId)
            }
        }

        @Test
        fun `manual valuation is rejected for non-fund assets`() {
            whenever(assetRepository.findById(assetId)).thenReturn(asset)
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)

            val command =
                buyCommand("10", "20.00").copy(
                    pricingMode = HoldingPricingMode.MANUAL,
                    manualValuation =
                        ManualFundValuation(
                            grossValue = BigDecimal("220.00"),
                            valuedAt = today,
                        ),
                )

            assertThrows<IllegalArgumentException> {
                service.record(command, accountId, userId)
            }
        }
    }

    // ── CUSTODY TRANSFER ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("CUSTODY_TRANSFER")
    inner class CustodyTransferTests {
        @Test
        fun `incoming transfer with price is treated as a purchase for average cost`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "28.00"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.CUSTODY_TRANSFER,
                    quantity = BigDecimal("50"),
                    unitPrice = BigDecimal("30.00"),
                    grossValue = BigDecimal("1500"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("150")
            // (100×28 + 50×30) / 150 = (2800+1500)/150 = 4300/150 ≈ 28.6667
            assertThat(updated.averageCost.setScale(4, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo(BigDecimal("4300").divide(BigDecimal("150"), 4, java.math.RoundingMode.HALF_UP))
        }

        @Test
        fun `outgoing transfer reduces quantity without changing averageCost`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("200", "30.00"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.OUTFLOW,
                    type = InvestmentTransactionType.CUSTODY_TRANSFER,
                    quantity = BigDecimal("80"),
                    grossValue = BigDecimal("2400"),
                    tradeDate = today,
                )

            service.record(command, accountId, userId)

            val captor = argumentCaptor<Holding>()
            verify(holdingRepository).update(captor.capture())

            val updated = captor.firstValue
            assertThat(updated.quantity).isEqualByComparingTo("120")
            assertThat(updated.averageCost).isEqualByComparingTo("30.00")
        }
    }

    // ── VALIDATION ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation")
    inner class ValidationTests {
        @Test
        fun `fails when financialAccountId does not belong to the caller accountId`() {
            whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(emptyList())
            whenever(financialAccountRepository.existsByIdAndAccountId(financialAccountId, accountId))
                .thenReturn(false)

            assertThrows<IllegalArgumentException> {
                service.record(buyCommand("100", "28.50"), accountId, userId)
            }

            verify(investmentTransactionRepository, never()).save(any())
        }

        @Test
        fun `fails when asset is not found`() {
            whenever(assetRepository.findById(assetId)).thenReturn(null)
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            assertThrows<NoSuchElementException> {
                service.record(buyCommand("100", "28.50"), accountId, userId)
            }
        }

        @Test
        fun `fails when grossValue is negative`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)

            val command = buyCommand("100", "28.50").copy(grossValue = BigDecimal("-1"))

            assertThrows<IllegalArgumentException> {
                service.record(command, accountId, userId)
            }
        }

        @Test
        fun `fails when type is OTHER and typeAdditionalInfo is missing`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("100", "28.50"))

            val command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.OTHER,
                    // required for OTHER
                    typeAdditionalInfo = null,
                    grossValue = BigDecimal("100"),
                    tradeDate = today,
                )

            assertThrows<IllegalArgumentException> {
                service.record(command, accountId, userId)
            }
        }

        @Test
        fun `fails when PURCHASE is missing quantity`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            val command = buyCommand("100", "28.50").copy(quantity = null)

            assertThrows<IllegalArgumentException> {
                service.record(command, accountId, userId)
            }
        }

        @Test
        fun `PURCHASE infers unitPrice from grossValue and quantity when unitPrice omitted`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            val command = buyCommand("100", "28.50").copy(unitPrice = null)

            service.record(command, accountId, userId)

            val txCaptor = argumentCaptor<InvestmentTransaction>()
            verify(investmentTransactionRepository).save(txCaptor.capture())
            val expected =
                BigDecimal("2850").divide(BigDecimal("100"), HoldingPositionEvolution.COST_SCALE, RoundingMode.HALF_UP)
            assertThat(txCaptor.firstValue.unitPrice).isEqualByComparingTo(expected)
        }

        @Test
        fun `fails when PURCHASE omits unitPrice and quantity is zero`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            val command = buyCommand("100", "28.50").copy(unitPrice = null, quantity = BigDecimal.ZERO)

            assertThrows<IllegalArgumentException> {
                service.record(command, accountId, userId)
            }
        }

        @Test
        fun `update infers unitPrice for PURCHASE when omitted`() {
            val txId = UUID.randomUUID()
            val existingTx =
                InvestmentTransaction(
                    id = txId,
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("2"),
                    unitPrice = BigDecimal("10"),
                    grossValue = BigDecimal("20"),
                    tradeDate = today,
                    source = InvestmentSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                )
            val updatedRef = AtomicReference<InvestmentTransaction>()
            whenever(investmentTransactionRepository.findById(txId, accountId)).thenReturn(existingTx)
            whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holdingWith("2", "10"))
            whenever(investmentTransactionRepository.update(any())).thenAnswer { inv ->
                val arg = inv.arguments[0] as InvestmentTransaction
                updatedRef.set(arg)
                savedTransaction(arg)
            }
            whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenAnswer {
                listOf(updatedRef.get() ?: error("expected update before recompute"))
            }
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val updateCmd =
                UpdateInvestmentTransactionCommand(
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("2"),
                    unitPrice = null,
                    grossValue = BigDecimal("22"),
                    tradeDate = today,
                )

            service.update(txId, updateCmd, accountId, userId)

            val updateCaptor = argumentCaptor<InvestmentTransaction>()
            verify(investmentTransactionRepository).update(updateCaptor.capture())
            assertThat(updateCaptor.firstValue.unitPrice).isEqualByComparingTo(
                BigDecimal("22").divide(BigDecimal("2"), HoldingPositionEvolution.COST_SCALE, RoundingMode.HALF_UP),
            )
        }
    }

    // ── DEDUP ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Provider deduplication")
    inner class DedupTests {
        @Test
        fun `skips saving when providerTransactionId already exists for the account`() {
            val existingProviderId = "ofb-tx-12345"
            whenever(
                investmentTransactionRepository.existsByProviderTransactionId(
                    financialAccountId,
                    existingProviderId,
                ),
            )
                .thenReturn(true)

            val command =
                buyCommand("100", "28.50").copy(
                    providerTransactionId = existingProviderId,
                    source = InvestmentSource.OPEN_FINANCE,
                )

            assertThrows<IllegalStateException> {
                service.record(command, accountId, userId)
            }

            verify(investmentTransactionRepository, never()).save(any())
        }

        @Test
        fun `saves normally when providerTransactionId is not a duplicate`() {
            whenever(investmentTransactionRepository.existsByProviderTransactionId(any(), any()))
                .thenReturn(false)
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            val command =
                buyCommand("100", "28.50").copy(
                    providerTransactionId = "ofb-tx-new",
                    source = InvestmentSource.OPEN_FINANCE,
                )

            service.record(command, accountId, userId)

            verify(investmentTransactionRepository).save(any())
        }
    }

    // ── HOLDING UPSERT ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Holding upsert")
    inner class HoldingUpsertTests {
        @Test
        fun `creates a new holding when none exists for the asset`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(null)
            whenever(holdingRepository.save(any())).thenAnswer { savedHolding(it.arguments[0] as Holding) }

            service.record(buyCommand("100", "28.50"), accountId, userId)

            verify(holdingRepository).save(any())
            verify(holdingRepository, never()).update(any())
        }

        @Test
        fun `updates an existing holding when one is found`() {
            whenever(holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId))
                .thenReturn(holdingWith("50", "25.00"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            service.record(buyCommand("50", "30.00"), accountId, userId)

            verify(holdingRepository, never()).save(any())
            verify(holdingRepository).update(any())
        }

        @Test
        fun `uses providerInvestmentId lookup when present`() {
            val providerId = "pluggy-inv-abc"
            whenever(holdingRepository.findByProviderInvestmentId(financialAccountId, providerId))
                .thenReturn(holdingWith("100", "28.50"))
            whenever(holdingRepository.update(any())).thenAnswer { it.arguments[0] as Holding }

            val command =
                buyCommand("50", "30.00").copy(
                    providerInvestmentId = providerId,
                    source = InvestmentSource.OPEN_FINANCE,
                )

            service.record(command, accountId, userId)

            verify(holdingRepository, never()).save(any())
            verify(holdingRepository).update(any())
        }
    }
}
