package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryResolver
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.transaction.TransactionCategoryService
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleService
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("TransactionCategoryResolver")
class TransactionCategoryResolverTest {
    private lateinit var categoryService: TransactionCategoryService
    private lateinit var ruleService: TransactionRuleService
    private lateinit var categoryRepository: TransactionCategoryRepository
    private lateinit var merchantRepository: MerchantRepository
    private lateinit var resolver: TransactionCategoryResolver

    private val accountId = UUID.randomUUID()
    private val principal =
        RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = UUID.randomUUID(),
            serviceId = null,
            token = "test-token",
            permissionsFromToken = emptyList(),
            currentAccountId = accountId,
        )

    @BeforeEach
    fun setUp() {
        categoryService = mock()
        ruleService = mock()
        categoryRepository = mock()
        merchantRepository = mock()
        resolver =
            TransactionCategoryResolver(
                service = categoryService,
                ruleService = ruleService,
                categoryRepository = categoryRepository,
                merchantRepository = merchantRepository,
            )
    }

    @Test
    fun `transactionRules forwards isSystem filter and returns 1-based position`() {
        val rules =
            listOf(
                rule(pattern = "ifood"),
                rule(pattern = "uber"),
            )
        whenever(ruleService.listRules(accountId, true)).thenReturn(rules)

        val result = resolver.transactionRules(isSystem = true, search = null, first = 20, after = null, principal = principal)

        verify(ruleService).listRules(accountId, true)
        assertThat(result.edges).hasSize(2)
        assertThat(result.edges[0].node.position).isEqualTo(1)
        assertThat(result.edges[1].node.position).isEqualTo(2)
        assertThat(result.edges.map { it.node.pattern }).containsExactly("ifood", "uber")
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

        val result = resolver.transactionRules(isSystem = null, search = null, first = 20, after = null, principal = principal)

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

        val result = resolver.transactionRules(isSystem = false, search = "ifood", first = 20, after = null, principal = principal)

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
    ) = TransactionRule(
        id = id,
        accountId = accountId,
        categoryId = null,
        merchantId = null,
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
}
