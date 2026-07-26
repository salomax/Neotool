package io.github.salomax.neotool.banking.graphql.resolver

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.preferences.AccountPreferences
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionOutcome
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.MonthSummary
import io.github.salomax.neotool.banking.domain.transaction.TagTotal
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionBelongsToOption
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.banking.model.dto.transaction.BulkCategorizeResult
import io.github.salomax.neotool.banking.model.dto.transaction.BulkOperationOutcome
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.model.dto.transaction.UpdateTransactionCommand
import io.github.salomax.neotool.banking.repo.attachment.TransactionAttachmentRepository
import io.github.salomax.neotool.banking.repo.import.nfce.NfceDocumentRepository
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyAlreadyClaimedException
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRecord
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRepository
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.pendingaction.AgentWriteConfirmationService
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.service.transaction.BulkWriteCapGuard
import io.github.salomax.neotool.banking.service.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.service.transaction.DashboardAnalyticsService
import io.github.salomax.neotool.banking.service.transaction.IdempotencyService
import io.github.salomax.neotool.banking.service.transaction.McpChangeLogService
import io.github.salomax.neotool.banking.service.transaction.McpWriteBulkConfiguration
import io.github.salomax.neotool.banking.service.transaction.MonthSummaryQueryService
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleEngine
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@DisplayName("TransactionResolver")
class TransactionResolverTest {
    private lateinit var service: TransactionService
    private lateinit var monthSummaryQueryService: MonthSummaryQueryService
    private lateinit var dashboardAnalyticsService: DashboardAnalyticsService
    private lateinit var creditCardBillService: CreditCardBillService
    private lateinit var mcpChangeLogService: McpChangeLogService
    private lateinit var merchantRepository: MerchantRepository
    private lateinit var transactionRuleEngine: TransactionRuleEngine
    private lateinit var transactionCategoryRepository: TransactionCategoryRepository
    private lateinit var accountPreferencesService: AccountPreferencesService
    private lateinit var nfceDocumentRepository: NfceDocumentRepository
    private lateinit var transactionAttachmentRepository: TransactionAttachmentRepository
    private lateinit var idempotencyService: IdempotencyService
    private lateinit var bulkWriteCapGuard: BulkWriteCapGuard
    private lateinit var agentWriteConfirmationService: AgentWriteConfirmationService
    private lateinit var resolver: TransactionResolver

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val now = Instant.now()
    private val today = LocalDate.now()

    private fun principal() =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = userId,
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    @BeforeEach
    fun setUp() {
        service = mock()
        monthSummaryQueryService = mock()
        dashboardAnalyticsService = mock()
        creditCardBillService = mock()
        mcpChangeLogService = mock()
        merchantRepository = mock()
        transactionRuleEngine = mock()
        transactionCategoryRepository = mock()
        accountPreferencesService = mock()
        nfceDocumentRepository = mock()
        transactionAttachmentRepository = mock()
        idempotencyService = mock()
        bulkWriteCapGuard = BulkWriteCapGuard(McpWriteBulkConfiguration(maxAffectedRecords = 50))
        agentWriteConfirmationService = mock()
        whenever(service.findAccessibleCategoriesByIds(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(emptyMap())
        whenever(service.findAccessibleCategory(anyOrNull(), org.mockito.kotlin.any())).thenReturn(null)
        whenever(accountPreferencesService.get(accountId)).thenReturn(AccountPreferences.defaultsFor(accountId, now))
        whenever(transactionRuleEngine.preloadRules(accountId)).thenReturn(emptyList())
        resolver =
            TransactionResolver(
                service = service,
                monthSummaryQueryService = monthSummaryQueryService,
                dashboardAnalyticsService = dashboardAnalyticsService,
                creditCardBillService = creditCardBillService,
                mcpChangeLogService = mcpChangeLogService,
                merchantRepository = merchantRepository,
                transactionRuleEngine = transactionRuleEngine,
                transactionCategoryRepository = transactionCategoryRepository,
                accountPreferencesService = accountPreferencesService,
                nfceDocumentRepository = nfceDocumentRepository,
                transactionAttachmentRepository = transactionAttachmentRepository,
                idempotencyService = idempotencyService,
                bulkWriteCapGuard = bulkWriteCapGuard,
                agentWriteConfirmationService = agentWriteConfirmationService,
            )
    }

    @Nested
    @DisplayName("createTransaction")
    inner class CreateTransactionTests {
        @Test
        fun `input map is correctly parsed into CreateTransactionCommand`() {
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "creditDebitType" to "DEBIT",
                    "type" to "OTHER_EXPENSE",
                    "amount" to 99.99,
                    "date" to today.toString(),
                    "description" to "Test payment",
                    "status" to "POSTED",
                    "currencyCode" to "BRL",
                    "notes" to "My notes",
                    "tags" to listOf("Food", "Weekend"),
                    "categoryId" to UUID.randomUUID().toString(),
                )
            val savedTx =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    status = TransactionStatus.POSTED,
                    amount = BigDecimal("99.99"),
                    date = today,
                    description = "Test payment",
                    notes = "My notes",
                    categoryId = null,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(service.createTransactionOutcome(org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull()))
                .thenReturn(CreateTransactionOutcome(transaction = savedTx))
            whenever(service.findAccessibleCategory(null, accountId)).thenReturn(null)

            resolver.createTransaction(input, principal())

            val commandCaptor = argumentCaptor<CreateTransactionCommand>()
            verify(service).createTransactionOutcome(commandCaptor.capture(), eq(accountId), eq(userId), anyOrNull())
            val command = commandCaptor.firstValue
            assertThat(command.financialAccountId).isEqualTo(financialAccountId)
            assertThat(command.creditDebitType).isEqualTo(CreditDebitType.DEBIT)
            assertThat(command.type).isEqualTo(TransactionType.OTHER_EXPENSE)
            assertThat(command.amount).isEqualByComparingTo("99.99")
            assertThat(command.date).isEqualTo(today)
            assertThat(command.description).isEqualTo("Test payment")
            assertThat(command.status).isEqualTo(TransactionStatus.POSTED)
            assertThat(command.currencyCode).isEqualTo("BRL")
            assertThat(command.notes).isEqualTo("My notes")
            assertThat(command.tags).containsExactly("Food", "Weekend")
            assertThat(command.belongsToUserId).isNull()
            assertThat(command.belongsToName).isNull()
        }

        @Test
        fun `agent-originated createTransaction replays stored result for the same key`() {
            val idempotentResolver = resolverWithRealIdempotency()
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "creditDebitType" to "DEBIT",
                    "type" to "OTHER_EXPENSE",
                    "amount" to 99.99,
                    "date" to today.toString(),
                    "description" to "Idempotent create",
                )
            whenever(service.createTransactionOutcome(org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull()))
                .thenReturn(CreateTransactionOutcome(transaction = transaction(description = "Idempotent create")))
            whenever(service.findAccessibleCategory(null, accountId)).thenReturn(null)

            val first =
                idempotentResolver.createTransaction(
                    input = input,
                    principal = principal(),
                    idempotencyKey = "create-key",
                    agentCallContext = agentCallContext(),
                )
            val second =
                idempotentResolver.createTransaction(
                    input = input,
                    principal = principal(),
                    idempotencyKey = "create-key",
                    agentCallContext = agentCallContext(),
                )

            assertThat(second).isEqualTo(first)
            verify(service, times(1)).createTransactionOutcome(
                org.mockito.kotlin.any(),
                eq(accountId),
                eq(userId),
                anyOrNull(),
            )
        }

        @Test
        fun `ui createTransaction bypasses idempotency service`() {
            val input =
                mapOf(
                    "financialAccountId" to financialAccountId.toString(),
                    "creditDebitType" to "DEBIT",
                    "type" to "OTHER_EXPENSE",
                    "amount" to 99.99,
                    "date" to today.toString(),
                    "description" to "UI create",
                )
            whenever(service.createTransactionOutcome(org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull()))
                .thenReturn(CreateTransactionOutcome(transaction = transaction(description = "UI create")))
            whenever(service.findAccessibleCategory(null, accountId)).thenReturn(null)

            resolver.createTransaction(input, principal())

            verify(service).createTransactionOutcome(org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull())
            verifyNoInteractions(idempotencyService)
        }
    }

    @Nested
    @DisplayName("bulkCategorize")
    inner class BulkCategorizeTests {
        @Test
        fun `agent over-cap returns confirmation required and never calls bulk service`() {
            val idempotentResolver = resolverWithRealIdempotency()
            whenever(
                service.countCategorizableTransactions(
                    listOf(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    accountId,
                ),
            ).thenReturn(51)
            whenever(
                agentWriteConfirmationService.requestBulkCategorize(
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(
                AccountPendingAction(
                    id = UUID.randomUUID(),
                    accountId = accountId,
                    type = PendingActionType.AGENT_WRITE_CONFIRMATION,
                    severity = PendingActionSeverity.ACTION_REQUIRED,
                    status = AccountPendingActionStatus.PENDING,
                    entityType = "BulkCategorizeConfirmationPayload",
                    entityId = UUID.randomUUID(),
                    relatedTransactionId = null,
                    payload = emptyMap(),
                    visibleFrom = now,
                    notifiedAt = null,
                    dismissedAt = null,
                    completedAt = null,
                    cancelledAt = null,
                    expiresAt = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val result =
                idempotentResolver.bulkCategorize(
                    transactionIds = listOf("00000000-0000-0000-0000-000000000001"),
                    categoryId = UUID.randomUUID().toString(),
                    principal = principal(),
                    idempotencyKey = "bulk-over-cap",
                    agentCallContext = agentCallContext(),
                )

            assertThat(result.outcome).isEqualTo(BulkOperationOutcome.CONFIRMATION_REQUIRED)
            assertThat(result.affectedCount).isEqualTo(51)
            assertThat(result.pendingAction).isNotNull
            verify(service, never()).bulkCategorize(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            )
        }

        @Test
        fun `agent at-cap executes bulk categorize once`() {
            val idempotentResolver = resolverWithRealIdempotency()
            val transactionId = UUID.fromString("00000000-0000-0000-0000-000000000002")
            val categoryId = UUID.randomUUID()
            whenever(service.countCategorizableTransactions(listOf(transactionId), accountId)).thenReturn(50)
            whenever(
                service.bulkCategorize(eq(listOf(transactionId)), eq(categoryId), eq(accountId), anyOrNull()),
            ).thenReturn(
                BulkCategorizeResult(affectedCount = 50, errors = emptyList()),
            )

            val result =
                idempotentResolver.bulkCategorize(
                    transactionIds = listOf(transactionId.toString()),
                    categoryId = categoryId.toString(),
                    principal = principal(),
                    idempotencyKey = "bulk-at-cap",
                    agentCallContext = agentCallContext(),
                )

            assertThat(result.outcome).isEqualTo(BulkOperationOutcome.EXECUTED)
            verify(service).bulkCategorize(
                eq(listOf(transactionId)),
                eq(categoryId),
                eq(accountId),
                anyOrNull(),
            )
        }

        @Test
        fun `ui path bypasses idempotency and cap gate`() {
            val transactionId = UUID.fromString("00000000-0000-0000-0000-000000000003")
            val categoryId = UUID.randomUUID()
            whenever(
                service.bulkCategorize(listOf(transactionId), categoryId, accountId, null),
            ).thenReturn(
                BulkCategorizeResult(affectedCount = 1, errors = emptyList()),
            )

            resolver.bulkCategorize(
                transactionIds = listOf(transactionId.toString()),
                categoryId = categoryId.toString(),
                principal = principal(),
            )

            verify(service).bulkCategorize(listOf(transactionId), categoryId, accountId, null)
            verify(service, never()).countCategorizableTransactions(anyOrNull(), anyOrNull())
            verifyNoInteractions(idempotencyService)
        }
    }

    @Nested
    @DisplayName("updateTransaction")
    inner class UpdateTransactionTests {
        @Test
        fun `partial input map produces correct UpdateTransactionCommand`() {
            val txId = UUID.randomUUID()
            val input =
                mapOf(
                    "description" to "Updated desc",
                    "amount" to 50.0,
                )
            val updatedTx =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("50.00"),
                    date = today,
                    description = "Updated desc",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(
                service.update(eq(txId), org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull()),
            ).thenReturn(updatedTx)
            whenever(service.findAccessibleCategory(null, accountId)).thenReturn(null)

            resolver.updateTransaction(txId.toString(), input, principal())

            val commandCaptor = argumentCaptor<UpdateTransactionCommand>()
            verify(service).update(eq(txId), commandCaptor.capture(), eq(accountId), eq(userId), anyOrNull())
            val command = commandCaptor.firstValue
            assertThat(command.description).isEqualTo("Updated desc")
            assertThat(command.amount).isEqualByComparingTo("50.00")
            assertThat(command.date).isNull()
            assertThat(command.notes).isNull()
            assertThat(command.tags).isNull()
            assertThat(command.tagsProvided).isFalse()
            assertThat(command.belongsToProvided).isFalse()
        }

        @Test
        fun `update input with belongsToName marks attribution as provided`() {
            val txId = UUID.randomUUID()
            val input =
                mapOf(
                    "belongsToName" to "Family",
                )
            val updatedTx =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    belongsToUserId = null,
                    belongsToName = "Family",
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("50.00"),
                    date = today,
                    description = "Updated desc",
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(
                service.update(eq(txId), org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull()),
            ).thenReturn(updatedTx)
            whenever(service.findAccessibleCategory(null, accountId)).thenReturn(null)

            resolver.updateTransaction(txId.toString(), input, principal())

            val commandCaptor = argumentCaptor<UpdateTransactionCommand>()
            verify(service).update(eq(txId), commandCaptor.capture(), eq(accountId), eq(userId), anyOrNull())
            assertThat(commandCaptor.firstValue.belongsToProvided).isTrue()
            assertThat(commandCaptor.firstValue.belongsToName).isEqualTo("Family")
            assertThat(commandCaptor.firstValue.belongsToUserId).isNull()
        }

        @Test
        fun `update input with tags marks tags as provided`() {
            val txId = UUID.randomUUID()
            val input =
                mapOf(
                    "tags" to listOf("Food", "Weekend"),
                )
            val updatedTx =
                Transaction(
                    id = txId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("50.00"),
                    date = today,
                    description = "Updated desc",
                    tags = listOf("Food", "Weekend"),
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(
                service.update(eq(txId), org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull()),
            ).thenReturn(updatedTx)
            whenever(service.findAccessibleCategory(null, accountId)).thenReturn(null)

            resolver.updateTransaction(txId.toString(), input, principal())

            val commandCaptor = argumentCaptor<UpdateTransactionCommand>()
            verify(service).update(eq(txId), commandCaptor.capture(), eq(accountId), eq(userId), anyOrNull())
            assertThat(commandCaptor.firstValue.tagsProvided).isTrue()
            assertThat(commandCaptor.firstValue.tags).containsExactly("Food", "Weekend")
        }

        @Test
        fun `agent-originated updateTransaction replays stored result for the same key`() {
            val idempotentResolver = resolverWithRealIdempotency()
            val txId = UUID.randomUUID()
            val input = mapOf("description" to "Updated once")
            whenever(
                service.update(eq(txId), org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull()),
            ).thenReturn(transaction(id = txId, description = "Updated once"))
            whenever(service.findAccessibleCategory(null, accountId)).thenReturn(null)

            val first =
                idempotentResolver.updateTransaction(
                    id = txId.toString(),
                    input = input,
                    principal = principal(),
                    idempotencyKey = "update-key",
                    agentCallContext = agentCallContext(),
                )
            val second =
                idempotentResolver.updateTransaction(
                    id = txId.toString(),
                    input = input,
                    principal = principal(),
                    idempotencyKey = "update-key",
                    agentCallContext = agentCallContext(),
                )

            assertThat(second).isEqualTo(first)
            verify(service, times(1)).update(eq(txId), org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull())
        }

        @Test
        fun `ui updateTransaction bypasses idempotency service`() {
            val txId = UUID.randomUUID()
            val input = mapOf("description" to "Updated desc")
            whenever(
                service.update(eq(txId), org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull()),
            ).thenReturn(transaction(id = txId, description = "Updated desc"))
            whenever(service.findAccessibleCategory(null, accountId)).thenReturn(null)

            resolver.updateTransaction(txId.toString(), input, principal())

            verify(service).update(eq(txId), org.mockito.kotlin.any(), eq(accountId), eq(userId), anyOrNull())
            verifyNoInteractions(idempotencyService)
        }
    }

    @Nested
    @DisplayName("deleteTransaction")
    inner class DeleteTransactionTests {
        @Test
        fun `delegates to service and returns deleted result`() {
            val txId = UUID.randomUUID()
            whenever(service.delete(eq(txId), eq(accountId), anyOrNull())).thenReturn(true)

            val result = resolver.deleteTransaction(txId.toString(), principal())

            assertThat(result.deleted).isTrue
            assertThat(result.pendingAction).isNull()
            verify(service).delete(eq(txId), eq(accountId), anyOrNull())
        }

        @Test
        fun `returns false when service returns false`() {
            val txId = UUID.randomUUID()
            whenever(service.delete(eq(txId), eq(accountId), anyOrNull())).thenReturn(false)

            val result = resolver.deleteTransaction(txId.toString(), principal())

            assertThat(result.deleted).isFalse
        }

        @Test
        fun `agent delete creates a pending action instead of deleting immediately`() {
            val idempotentResolver = resolverWithRealIdempotency()
            val txId = UUID.randomUUID()
            val agentContext = agentCallContext()
            val pending =
                AccountPendingAction(
                    id = UUID.randomUUID(),
                    accountId = accountId,
                    type = PendingActionType.AGENT_WRITE_CONFIRMATION,
                    severity = PendingActionSeverity.ACTION_REQUIRED,
                    status = AccountPendingActionStatus.PENDING,
                    entityType = "DeleteTransactionConfirmationPayload",
                    entityId = txId,
                    relatedTransactionId = null,
                    payload =
                        mapOf(
                            "operation" to "delete_transaction",
                            "request_id" to txId.toString(),
                            "transaction_id" to txId.toString(),
                            "origin" to
                                mapOf(
                                    "client_id" to "mcp-client",
                                    "tool_call_id" to agentContext.toolCallId.toString(),
                                    "token_id" to "origin-token-id",
                                ),
                        ),
                    visibleFrom = now,
                    notifiedAt = null,
                    dismissedAt = null,
                    completedAt = null,
                    cancelledAt = null,
                    expiresAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(
                agentWriteConfirmationService.requestDeleteTransaction(eq(accountId), eq(txId), anyOrNull()),
            ).thenReturn(pending)

            val result =
                idempotentResolver.deleteTransaction(
                    id = txId.toString(),
                    principal = principal(),
                    idempotencyKey = "delete-key",
                    agentCallContext = agentContext,
                )

            assertThat(result.deleted).isFalse
            assertThat(result.pendingAction).isNotNull
            assertThat(result.pendingAction?.type).isEqualTo(PendingActionType.AGENT_WRITE_CONFIRMATION.name)
            verify(service, never()).delete(anyOrNull(), anyOrNull(), anyOrNull())
        }
    }

    @Nested
    @DisplayName("transactions")
    inner class TransactionsTests {
        @Test
        fun `resolves and attaches correct category DTO to each transaction`() {
            val catId = UUID.randomUUID()
            val category =
                TransactionCategory(
                    id = catId,
                    accountId = null,
                    parentId = null,
                    name = "Food",
                    applicableTo = io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo.EXPENSE,
                    icon = "x",
                    color = "#fff",
                    isSystem = true,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            val tx =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "Lunch",
                    categoryId = catId,
                    createdAt = now,
                    updatedAt = now,
                )
            val connection =
                io.github.salomax.neotool.common.graphql.pagination.Connection(
                    edges =
                        listOf(
                            io.github.salomax.neotool.common.graphql.pagination.Edge(
                                node = tx,
                                cursor = "cursor-0",
                            ),
                        ),
                    pageInfo =
                        io.github.salomax.neotool.common.graphql.pagination.PageInfo(
                            hasNextPage = false,
                            hasPreviousPage = false,
                            startCursor = "cursor-0",
                            endCursor = "cursor-0",
                        ),
                    totalCount = 1,
                )
            doReturn(connection).whenever(service).listTransactions(
                org.mockito.kotlin.any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            )
            whenever(service.findAccessibleCategoriesByIds(setOf(catId), accountId))
                .thenReturn(mapOf(catId to category))

            val result =
                resolver.transactions(
                    financialAccountId = financialAccountId.toString(),
                    belongsToUserId = null,
                    belongsToName = null,
                    search = null,
                    tags = null,
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    creditDebitType = null,
                    types = null,
                    month = today.monthValue,
                    year = today.year,
                    first = 10,
                    after = null,
                    principal = principal(),
                )

            assertThat(result.edges).hasSize(1)
            val edge = result.edges[0]
            assertThat(edge.node.id).isEqualTo(tx.id)
            assertThat(edge.node.description).isEqualTo("Lunch")
            assertThat(edge.node.category).isNotNull
            assertThat(edge.node.category!!.id).isEqualTo(catId)
            assertThat(edge.node.category!!.name).isEqualTo("Food")
            assertThat(result.pageInfo.hasNextPage).isFalse()
            assertThat(result.pageInfo.hasPreviousPage).isFalse()
        }

        @Test
        fun `search month and year filters are forwarded`() {
            val emptyConnection =
                io.github.salomax.neotool.common.graphql.pagination.Connection<Transaction>(
                    edges = emptyList(),
                    pageInfo =
                        io.github.salomax.neotool.common.graphql.pagination.PageInfo(
                            hasNextPage = false,
                            hasPreviousPage = false,
                            startCursor = null,
                            endCursor = null,
                        ),
                    totalCount = 0L,
                )
            doReturn(emptyConnection).whenever(service).listTransactions(
                org.mockito.kotlin.any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            )
            resolver.transactions(
                financialAccountId = financialAccountId.toString(),
                belongsToUserId = null,
                belongsToName = null,
                search = "coffee",
                tags = null,
                billId = null,
                categoryId = null,
                uncategorized = null,
                creditDebitType = null,
                types = null,
                month = 1,
                year = 2025,
                first = 20,
                after = null,
                principal = principal(),
            )

            verify(service).listTransactions(
                eq(accountId),
                eq(financialAccountId),
                anyOrNull(),
                anyOrNull(),
                eq("coffee"),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                eq(1),
                eq(2025),
                eq(20),
                anyOrNull(),
            )
        }

        @Test
        fun `category is null when service does not return accessible category mapping`() {
            val catId = UUID.randomUUID()
            val tx =
                Transaction(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = userId,
                    creditDebitType = CreditDebitType.DEBIT,
                    type = TransactionType.OTHER_EXPENSE,
                    amount = BigDecimal("10.00"),
                    date = today,
                    description = "Lunch",
                    categoryId = catId,
                    createdAt = now,
                    updatedAt = now,
                )
            val connection =
                io.github.salomax.neotool.common.graphql.pagination.Connection(
                    edges =
                        listOf(
                            io.github.salomax.neotool.common.graphql.pagination.Edge(
                                node = tx,
                                cursor = "cursor-0",
                            ),
                        ),
                    pageInfo =
                        io.github.salomax.neotool.common.graphql.pagination.PageInfo(
                            hasNextPage = false,
                            hasPreviousPage = false,
                            startCursor = "cursor-0",
                            endCursor = "cursor-0",
                        ),
                    totalCount = 1,
                )
            doReturn(connection).whenever(service).listTransactions(
                org.mockito.kotlin.any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            )
            whenever(service.findAccessibleCategoriesByIds(setOf(catId), accountId)).thenReturn(emptyMap())

            val result =
                resolver.transactions(
                    financialAccountId = financialAccountId.toString(),
                    belongsToUserId = null,
                    belongsToName = null,
                    search = null,
                    tags = null,
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    creditDebitType = null,
                    types = null,
                    month = today.monthValue,
                    year = today.year,
                    first = 10,
                    after = null,
                    principal = principal(),
                )

            assertThat(result.edges).hasSize(1)
            assertThat(result.edges[0].node.category).isNull()
        }
    }

    @Nested
    @DisplayName("transactionCategories")
    inner class TransactionCategoriesTests {
        @Test
        fun `returns full list for principal account`() {
            val categories =
                listOf(
                    TransactionCategory(
                        id = UUID.randomUUID(),
                        accountId = null,
                        parentId = null,
                        name = "System",
                        applicableTo = io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo.ANY,
                        icon = null,
                        color = null,
                        isSystem = true,
                        sortOrder = 0,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            whenever(service.listCategories(accountId)).thenReturn(categories)

            val result = resolver.transactionCategories(principal())

            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("System")
            verify(service).listCategories(accountId)
        }
    }

    @Nested
    @DisplayName("invalid UUID")
    inner class InvalidUuidTests {
        @Test
        fun `transactions fails gracefully when financialAccountId is not valid UUID`() {
            assertThrows<IllegalArgumentException> {
                resolver.transactions(
                    financialAccountId = "not-a-uuid",
                    belongsToUserId = null,
                    belongsToName = null,
                    search = null,
                    tags = null,
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    creditDebitType = null,
                    types = null,
                    month = null,
                    year = null,
                    first = null,
                    after = null,
                    principal = principal(),
                )
            }
        }

        @Test
        fun `createTransaction fails when financialAccountId in input is not valid UUID`() {
            val input =
                mapOf(
                    "financialAccountId" to "invalid",
                    "creditDebitType" to "DEBIT",
                    "type" to "OTHER_EXPENSE",
                    "amount" to 10.0,
                    "date" to today.toString(),
                    "description" to "X",
                )
            assertThrows<IllegalArgumentException> {
                resolver.createTransaction(input, principal())
            }
        }

        @Test
        fun `deleteTransaction fails when id is not valid UUID`() {
            assertThrows<IllegalArgumentException> {
                resolver.deleteTransaction("not-a-uuid", principal())
            }
        }
    }

    @Nested
    @DisplayName("transactionBelongsToOptions")
    inner class TransactionBelongsToOptionsTests {
        @Test
        fun `returns distinct options from service`() {
            whenever(service.listBelongsToOptions(accountId)).thenReturn(
                listOf(
                    TransactionBelongsToOption(userId = userId),
                    TransactionBelongsToOption(name = "Partner"),
                ),
            )

            val result = resolver.transactionBelongsToOptions(principal())

            assertThat(result).hasSize(2)
            assertThat(result[0].belongsToUserId).isEqualTo(userId)
            assertThat(result[0].belongsToName).isNull()
            assertThat(result[1].belongsToUserId).isNull()
            assertThat(result[1].belongsToName).isEqualTo("Partner")
        }
    }

    @Nested
    @DisplayName("transactionTags")
    inner class TransactionTagsTests {
        @Test
        fun `returns distinct tags from service`() {
            whenever(service.listTags(accountId)).thenReturn(listOf("Food", "Bills"))

            val result = resolver.transactionTags(principal())

            assertThat(result).hasSize(2)
            assertThat(result[0].tag).isEqualTo("Food")
            assertThat(result[1].tag).isEqualTo("Bills")
        }
    }

    @Nested
    @DisplayName("monthSummary")
    inner class MonthSummaryTests {
        private fun summary(
            year: Int = 2024,
            month: Int = 1,
        ) = MonthSummary(
            year = year,
            month = month,
            totalIncome = BigDecimal("500.00"),
            totalExpenses = BigDecimal("200.00"),
            closingBalance = BigDecimal("3000.00"),
            openingBalance = BigDecimal("2700.00"),
            transfersIn = BigDecimal.ZERO,
            transfersOut = BigDecimal.ZERO,
            projectedClosingBalance = null,
            isCurrentMonth = false,
            tagsSummary =
                listOf(
                    TagTotal(
                        tag = "Food",
                        totalAmount = BigDecimal("200.00"),
                        transactionCount = 2,
                        percentage = 100.0,
                    ),
                ),
        )

        @Test
        fun `with financialAccountId calls getMonthSummary`() {
            val expected = summary()
            whenever(monthSummaryQueryService.getMonthSummary(financialAccountId, accountId, 2024, 1))
                .thenReturn(expected)

            val result = resolver.monthSummary(financialAccountId.toString(), 2024, 1, principal())

            assertThat(result.closingBalance).isEqualTo(3000.0)
            assertThat(result.totalIncome).isEqualTo(500.0)
            assertThat(result.tagsSummary).hasSize(1)
            assertThat(result.tagsSummary.first().tag).isEqualTo("Food")
            verify(monthSummaryQueryService).getMonthSummary(financialAccountId, accountId, 2024, 1)
        }

        @Test
        fun `with null financialAccountId calls getAggregateMonthlySummary`() {
            val expected = summary()
            whenever(monthSummaryQueryService.getAggregateMonthlySummary(accountId, 2024, 1))
                .thenReturn(expected)

            val result = resolver.monthSummary(null, 2024, 1, principal())

            assertThat(result.closingBalance).isEqualTo(3000.0)
            verify(monthSummaryQueryService).getAggregateMonthlySummary(accountId, 2024, 1)
        }
    }

    @Nested
    @DisplayName("transactionsByCategory")
    inner class TransactionsByCategoryTests {
        @Test
        fun `returns uncategorized bucket with nullable category`() {
            whenever(
                service.getCategoryBreakdown(
                    accountId = eq(accountId),
                    financialAccountId = anyOrNull(),
                    belongsToUserId = anyOrNull(),
                    belongsToName = anyOrNull(),
                    search = anyOrNull(),
                    tags = anyOrNull(),
                    billId = anyOrNull(),
                    categoryId = anyOrNull(),
                    uncategorized = anyOrNull(),
                    month = eq(3),
                    year = eq(2024),
                    applicableTo =
                        eq(
                            io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo.EXPENSE,
                        ),
                ),
            ).thenReturn(
                listOf(
                    io.github.salomax.neotool.banking.domain.transaction.CategoryTotal(
                        category = null,
                        uncategorized = true,
                        totalAmount = BigDecimal("42.00"),
                        transactionCount = 2,
                        percentage = 100.0,
                    ),
                ),
            )

            val result =
                resolver.transactionsByCategory(
                    financialAccountId = null,
                    belongsToUserId = null,
                    belongsToName = null,
                    search = null,
                    tags = null,
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    year = 2024,
                    month = 3,
                    applicableTo = "EXPENSE",
                    principal = principal(),
                )

            assertThat(result).hasSize(1)
            assertThat(result.first().uncategorized).isTrue()
            assertThat(result.first().category).isNull()
            assertThat(result.first().totalAmount).isEqualTo(42.0)
        }
    }

    @Nested
    @DisplayName("transactionsByTag")
    inner class TransactionsByTagTests {
        @Test
        fun `returns tag totals from service`() {
            whenever(
                service.getTagBreakdown(
                    accountId = eq(accountId),
                    financialAccountId = anyOrNull(),
                    belongsToUserId = anyOrNull(),
                    belongsToName = anyOrNull(),
                    search = anyOrNull(),
                    tags = anyOrNull(),
                    billId = anyOrNull(),
                    categoryId = anyOrNull(),
                    uncategorized = anyOrNull(),
                    month = eq(3),
                    year = eq(2024),
                    applicableTo =
                        eq(
                            io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo.EXPENSE,
                        ),
                ),
            ).thenReturn(
                listOf(
                    TagTotal(
                        tag = "Weekend",
                        totalAmount = BigDecimal("42.00"),
                        transactionCount = 2,
                        percentage = 100.0,
                    ),
                ),
            )

            val result =
                resolver.transactionsByTag(
                    financialAccountId = null,
                    belongsToUserId = null,
                    belongsToName = null,
                    search = null,
                    tags = listOf("Weekend"),
                    billId = null,
                    categoryId = null,
                    uncategorized = null,
                    year = 2024,
                    month = 3,
                    applicableTo = "EXPENSE",
                    principal = principal(),
                )

            assertThat(result).hasSize(1)
            assertThat(result.first().tag).isEqualTo("Weekend")
            assertThat(result.first().totalAmount).isEqualTo(42.0)
        }
    }

    private fun resolverWithRealIdempotency(): TransactionResolver =
        TransactionResolver(
            service = service,
            monthSummaryQueryService = monthSummaryQueryService,
            dashboardAnalyticsService = dashboardAnalyticsService,
            creditCardBillService = creditCardBillService,
            mcpChangeLogService = mcpChangeLogService,
            merchantRepository = merchantRepository,
            transactionRuleEngine = transactionRuleEngine,
            transactionCategoryRepository = transactionCategoryRepository,
            accountPreferencesService = accountPreferencesService,
            nfceDocumentRepository = nfceDocumentRepository,
            transactionAttachmentRepository = transactionAttachmentRepository,
            idempotencyService =
                IdempotencyService(
                    repository = InMemoryMcpWriteIdempotencyRepository(),
                    objectMapper = ObjectMapper().findAndRegisterModules(),
                ),
            bulkWriteCapGuard = BulkWriteCapGuard(McpWriteBulkConfiguration(maxAffectedRecords = 50)),
            agentWriteConfirmationService = agentWriteConfirmationService,
        )

    private fun agentCallContext(): AgentCallContext =
        AgentCallContext(
            toolCallId = UUID.randomUUID(),
            originClientId = "mcp-client",
            originTokenId = "origin-token-id",
        )

    private fun transaction(
        id: UUID = UUID.randomUUID(),
        description: String,
    ) = Transaction(
        id = id,
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = userId,
        creditDebitType = CreditDebitType.DEBIT,
        type = TransactionType.OTHER_EXPENSE,
        status = TransactionStatus.POSTED,
        amount = BigDecimal("99.99"),
        date = today,
        description = description,
        createdAt = now,
        updatedAt = now,
    )

    private class InMemoryMcpWriteIdempotencyRepository : McpWriteIdempotencyRepository {
        private val rows = ConcurrentHashMap<Pair<UUID, String>, McpWriteIdempotencyRecord>()

        override fun claim(
            accountId: UUID,
            idempotencyKey: String,
            operation: String,
        ): McpWriteIdempotencyRecord {
            val record =
                McpWriteIdempotencyRecord(
                    id = UUID.randomUUID(),
                    accountId = accountId,
                    idempotencyKey = idempotencyKey,
                    operation = operation,
                    resultJson = "{}",
                    createdAt = Instant.now(),
                )
            val previous = rows.putIfAbsent(accountId to idempotencyKey, record)
            if (previous != null) {
                throw McpWriteIdempotencyAlreadyClaimedException(accountId, idempotencyKey, operation)
            }
            return record
        }

        override fun findByAccountIdAndIdempotencyKey(
            accountId: UUID,
            idempotencyKey: String,
        ): McpWriteIdempotencyRecord? = rows[accountId to idempotencyKey]

        override fun storeResult(
            id: UUID,
            resultJson: String,
        ): McpWriteIdempotencyRecord {
            val existing = rows.values.first { it.id == id }
            val updated = existing.copy(resultJson = resultJson)
            rows[existing.accountId to existing.idempotencyKey] = updated
            return updated
        }

        override fun deleteCreatedBefore(cutoff: Instant): Int = 0
    }
}
