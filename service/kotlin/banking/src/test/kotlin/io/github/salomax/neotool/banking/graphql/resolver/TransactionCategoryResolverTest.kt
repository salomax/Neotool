package io.github.salomax.neotool.banking.graphql.resolver

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingActionStatus
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionSeverity
import io.github.salomax.neotool.banking.domain.pendingaction.PendingActionType
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryResolver
import io.github.salomax.neotool.banking.model.dto.transaction.BulkCategorizeResult
import io.github.salomax.neotool.banking.model.dto.transaction.BulkOperationOutcome
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionRuleInput
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyAlreadyClaimedException
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRecord
import io.github.salomax.neotool.banking.repo.transaction.McpWriteIdempotencyRepository
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.pendingaction.AgentWriteConfirmationService
import io.github.salomax.neotool.banking.service.transaction.BulkWriteCapGuard
import io.github.salomax.neotool.banking.service.transaction.IdempotencyService
import io.github.salomax.neotool.banking.service.transaction.McpWriteBulkConfiguration
import io.github.salomax.neotool.banking.service.transaction.TransactionCategoryService
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleService
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@DisplayName("TransactionCategoryResolver")
class TransactionCategoryResolverTest {
    private lateinit var categoryService: TransactionCategoryService
    private lateinit var ruleService: TransactionRuleService
    private lateinit var categoryRepository: TransactionCategoryRepository
    private lateinit var merchantRepository: MerchantRepository
    private lateinit var idempotencyService: IdempotencyService
    private lateinit var bulkWriteCapGuard: BulkWriteCapGuard
    private lateinit var agentWriteConfirmationService: AgentWriteConfirmationService
    private lateinit var resolver: TransactionCategoryResolver

    private val accountId = UUID.randomUUID()
    private val principal =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = UUID.randomUUID(),
            serviceId = null,
            token = "test-token",
            tokenId = "test-token-id",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    @BeforeEach
    fun setUp() {
        categoryService = mock()
        ruleService = mock()
        categoryRepository = mock()
        merchantRepository = mock()
        idempotencyService = mock()
        bulkWriteCapGuard = BulkWriteCapGuard(McpWriteBulkConfiguration(maxAffectedRecords = 50))
        agentWriteConfirmationService = mock()
        resolver =
            TransactionCategoryResolver(
                service = categoryService,
                ruleService = ruleService,
                categoryRepository = categoryRepository,
                merchantRepository = merchantRepository,
                idempotencyService = idempotencyService,
                bulkWriteCapGuard = bulkWriteCapGuard,
                agentWriteConfirmationService = agentWriteConfirmationService,
            )
    }

    @Test
    fun `transactionRules forwards isSystem filter and returns 1-based position`() {
        val rules =
            listOf(
                rule(pattern = "ifood", typeOverride = CategoryApplicableTo.TRANSFER),
                rule(pattern = "uber"),
            )
        whenever(ruleService.listRules(accountId, true)).thenReturn(rules)

        val result =
            resolver.transactionRules(
                isSystem = true,
                search = null,
                first = 20,
                after = null,
                principal = principal,
            )

        verify(ruleService).listRules(accountId, true)
        assertThat(result.edges).hasSize(2)
        assertThat(result.edges[0].node.position).isEqualTo(1)
        assertThat(result.edges[0].node.typeOverride).isEqualTo(CategoryApplicableTo.TRANSFER)
        assertThat(result.edges[1].node.position).isEqualTo(2)
        assertThat(result.edges.map { it.node.pattern }).containsExactly("ifood", "uber")
    }

    @Test
    fun `createTransactionRule maps typeOverride from GraphQL input`() {
        val created = rule(pattern = "pix joao", categoryId = null, typeOverride = CategoryApplicableTo.TRANSFER)
        whenever(ruleService.create(org.mockito.kotlin.any(), eq(accountId), anyOrNull())).thenReturn(created)

        val result =
            resolver.createTransactionRule(
                input =
                    mapOf(
                        "pattern" to "pix joao",
                        "patternType" to "CONTAINS",
                        "applicableTo" to "EXPENSE",
                        "typeOverride" to "TRANSFER",
                    ),
                principal = principal,
            )

        val captor = argumentCaptor<CreateTransactionRuleInput>()
        verify(ruleService).create(captor.capture(), eq(accountId), anyOrNull())
        assertThat(captor.firstValue.typeOverride).isEqualTo(CategoryApplicableTo.TRANSFER)
        assertThat(result.typeOverride).isEqualTo(CategoryApplicableTo.TRANSFER)
    }

    @Test
    fun `agent-originated createTransactionRule replays stored result for the same key`() {
        val idempotentResolver = resolverWithRealIdempotency()
        val created = rule(pattern = "pix joao", categoryId = null, typeOverride = CategoryApplicableTo.TRANSFER)
        whenever(ruleService.create(org.mockito.kotlin.any(), eq(accountId), anyOrNull())).thenReturn(created)

        val first =
            idempotentResolver.createTransactionRule(
                input =
                    mapOf(
                        "pattern" to "pix joao",
                        "patternType" to "CONTAINS",
                        "applicableTo" to "EXPENSE",
                        "typeOverride" to "TRANSFER",
                    ),
                principal = principal,
                idempotencyKey = "rule-key",
                agentCallContext = agentCallContext(),
            )
        val second =
            idempotentResolver.createTransactionRule(
                input =
                    mapOf(
                        "pattern" to "pix joao",
                        "patternType" to "CONTAINS",
                        "applicableTo" to "EXPENSE",
                        "typeOverride" to "TRANSFER",
                    ),
                principal = principal,
                idempotencyKey = "rule-key",
                agentCallContext = agentCallContext(),
            )

        assertThat(second).isEqualTo(first)
        verify(ruleService, times(1)).create(org.mockito.kotlin.any(), eq(accountId), anyOrNull())
    }

    @Test
    fun `ui createTransactionRule bypasses idempotency service`() {
        val created = rule(pattern = "pix joao", categoryId = null, typeOverride = CategoryApplicableTo.TRANSFER)
        whenever(ruleService.create(org.mockito.kotlin.any(), eq(accountId), anyOrNull())).thenReturn(created)

        resolver.createTransactionRule(
            input =
                mapOf(
                    "pattern" to "pix joao",
                    "patternType" to "CONTAINS",
                    "applicableTo" to "EXPENSE",
                    "typeOverride" to "TRANSFER",
                ),
            principal = principal,
        )

        verify(ruleService).create(org.mockito.kotlin.any(), eq(accountId), anyOrNull())
        verifyNoInteractions(idempotencyService)
    }

    @Test
    fun `agent over-cap applyTransactionRuleToHistorical returns confirmation required and never mutates`() {
        val idempotentResolver = resolverWithRealIdempotency()
        val ruleId = UUID.randomUUID()
        whenever(ruleService.countHistoricalMatches(ruleId, accountId)).thenReturn(51)
        whenever(agentWriteConfirmationService.requestApplyRuleToHistorical(anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(
                AccountPendingAction(
                    id = UUID.randomUUID(),
                    accountId = accountId,
                    type = PendingActionType.AGENT_WRITE_CONFIRMATION,
                    severity = PendingActionSeverity.ACTION_REQUIRED,
                    status = AccountPendingActionStatus.PENDING,
                    entityType = "ApplyRuleToHistoricalConfirmationPayload",
                    entityId = UUID.randomUUID(),
                    relatedTransactionId = null,
                    payload = emptyMap(),
                    visibleFrom = Instant.now(),
                    notifiedAt = null,
                    dismissedAt = null,
                    completedAt = null,
                    cancelledAt = null,
                    expiresAt = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )

        val result =
            idempotentResolver.applyTransactionRuleToHistorical(
                id = ruleId.toString(),
                principal = principal,
                idempotencyKey = "apply-over-cap",
                agentCallContext = agentCallContext(),
            )

        assertThat(result.outcome).isEqualTo(BulkOperationOutcome.CONFIRMATION_REQUIRED)
        assertThat(result.affectedCount).isEqualTo(51)
        assertThat(result.pendingAction).isNotNull
        verify(ruleService, never()).applyToHistorical(anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `agent at-cap applyTransactionRuleToHistorical executes once`() {
        val idempotentResolver = resolverWithRealIdempotency()
        val ruleId = UUID.randomUUID()
        whenever(ruleService.countHistoricalMatches(ruleId, accountId)).thenReturn(50)
        whenever(ruleService.applyToHistorical(eq(ruleId), eq(accountId), anyOrNull()))
            .thenReturn(BulkCategorizeResult(affectedCount = 50, errors = emptyList()))

        val result =
            idempotentResolver.applyTransactionRuleToHistorical(
                id = ruleId.toString(),
                principal = principal,
                idempotencyKey = "apply-at-cap",
                agentCallContext = agentCallContext(),
            )

        assertThat(result.outcome).isEqualTo(BulkOperationOutcome.EXECUTED)
        verify(ruleService).applyToHistorical(eq(ruleId), eq(accountId), anyOrNull())
    }

    @Test
    fun `ui applyTransactionRuleToHistorical bypasses idempotency and cap gate`() {
        val ruleId = UUID.randomUUID()
        whenever(ruleService.applyToHistorical(ruleId, accountId, null))
            .thenReturn(BulkCategorizeResult(affectedCount = 1, errors = emptyList()))

        resolver.applyTransactionRuleToHistorical(
            id = ruleId.toString(),
            principal = principal,
        )

        verify(ruleService).applyToHistorical(ruleId, accountId, null)
        verify(ruleService, never()).countHistoricalMatches(anyOrNull(), anyOrNull())
        verifyNoInteractions(idempotencyService)
    }

    @Test
    fun `reorderTransactionRules returns reindexed positions`() {
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val reordered =
            listOf(
                rule(id = secondId, pattern = "second"),
                rule(id = firstId, pattern = "first"),
            )
        whenever(ruleService.reorder(listOf(secondId, firstId), accountId)).thenReturn(reordered)

        val result = resolver.reorderTransactionRules(listOf(secondId.toString(), firstId.toString()), principal)

        verify(ruleService).reorder(eq(listOf(secondId, firstId)), eq(accountId))
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(secondId)
        assertThat(result[0].position).isEqualTo(1)
        assertThat(result[1].id).isEqualTo(firstId)
        assertThat(result[1].position).isEqualTo(2)
    }

    @Test
    fun `transactionRules with null filter returns all rules`() {
        whenever(ruleService.listRules(accountId, null)).thenReturn(emptyList())

        val result =
            resolver.transactionRules(
                isSystem = null,
                search = null,
                first = 20,
                after = null,
                principal = principal,
            )

        verify(ruleService).listRules(accountId, null)
        assertThat(result.edges).isEmpty()
    }

    @Test
    fun `transactionRules applies backend text search case-insensitively before pagination`() {
        val rules =
            listOf(
                rule(pattern = "iFood delivery"),
                rule(pattern = "Uber trip"),
                rule(pattern = "IFOOD mercado"),
            )
        whenever(ruleService.listRules(accountId, false)).thenReturn(rules)

        val result =
            resolver.transactionRules(
                isSystem = false,
                search = "ifood",
                first = 20,
                after = null,
                principal = principal,
            )

        verify(ruleService).listRules(accountId, false)
        assertThat(result.edges).hasSize(2)
        assertThat(result.edges.map { it.node.pattern }).containsExactly("iFood delivery", "IFOOD mercado")
        assertThat(result.totalCount).isEqualTo(2)
    }

    @Test
    fun `transactionRulesByIds preserves requested order and original evaluation position`() {
        val first = rule(pattern = "ifood")
        val second = rule(pattern = "uber")
        whenever(ruleService.listRules(accountId, null)).thenReturn(listOf(first, second))

        val result =
            resolver.transactionRulesByIds(
                ids = listOf(second.id.toString(), first.id.toString()),
                principal = principal,
            )

        verify(ruleService).listRules(accountId, null)
        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly(second.id, first.id)
        assertThat(result.map { it.position }).containsExactly(2, 1)
    }

    private fun rule(
        id: UUID = UUID.randomUUID(),
        pattern: String = "rule",
        categoryId: UUID? = UUID.randomUUID(),
        typeOverride: CategoryApplicableTo? = null,
    ) = TransactionRule(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        merchantId = null,
        typeOverride = typeOverride,
        descriptionOverride = null,
        tagsOverride = null,
        pattern = pattern,
        patternType = PatternType.CONTAINS,
        applicableTo = CategoryApplicableTo.ANY,
        priority = 10,
        isSystem = false,
        isImplicit = false,
        matchCount = 0,
        deletedAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun resolverWithRealIdempotency(): TransactionCategoryResolver =
        TransactionCategoryResolver(
            service = categoryService,
            ruleService = ruleService,
            categoryRepository = categoryRepository,
            merchantRepository = merchantRepository,
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
