package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRuleRepository
import io.github.salomax.neotool.banking.service.transaction.MerchantLogoHydrationService
import io.github.salomax.neotool.banking.service.transaction.MerchantService
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("MerchantService")
class MerchantServiceTest {
    private lateinit var merchantRepository: MerchantRepository
    private lateinit var ruleService: TransactionRuleService
    private lateinit var ruleRepository: TransactionRuleRepository
    private lateinit var merchantLogoHydrationService: MerchantLogoHydrationService
    private lateinit var service: MerchantService

    private val accountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        merchantRepository = mock()
        ruleService = mock()
        ruleRepository = mock()
        merchantLogoHydrationService = mock()
        service = MerchantService(merchantRepository, ruleService, ruleRepository, merchantLogoHydrationService)
    }

    @Test
    fun `create enriches existing matching rule when merchant rule already exists`() {
        val merchantId = UUID.randomUUID()
        val merchant =
            Merchant(
                id = merchantId,
                name = "Netflix",
                domain = "netflix.com",
                sector = "streaming",
                isSystem = false,
                accountId = accountId,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val existingRule =
            TransactionRule(
                id = UUID.randomUUID(),
                accountId = accountId,
                categoryId = UUID.randomUUID(),
                merchantId = null,
                pattern = "netflix",
                patternType = PatternType.CONTAINS,
                applicableTo = CategoryApplicableTo.ANY,
                priority = 60,
                isImplicit = true,
                createdAt = Instant.now().minusSeconds(60),
                updatedAt = Instant.now().minusSeconds(60),
            )

        whenever(merchantRepository.save(any())).thenReturn(merchant)
        doThrow(DuplicateTransactionRuleException("netflix", PatternType.CONTAINS))
            .whenever(ruleService)
            .create(any(), eq(accountId), anyOrNull())
        whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(listOf(existingRule))
        whenever(ruleRepository.save(any())).thenAnswer { it.arguments[0] as TransactionRule }

        val created = service.create("Netflix", "https://www.netflix.com/plans", "streaming", accountId)

        assertThat(created.id).isEqualTo(merchantId)
        val captor = argumentCaptor<TransactionRule>()
        verify(ruleRepository).save(captor.capture())
        assertThat(captor.firstValue.id).isEqualTo(existingRule.id)
        assertThat(captor.firstValue.merchantId).isEqualTo(merchantId)
        assertThat(captor.firstValue.categoryId).isEqualTo(existingRule.categoryId)
        verify(merchantLogoHydrationService).hydrateForDomainAsync("netflix.com")
    }

    @Test
    fun `create does not overwrite existing merchant on matching rule`() {
        val merchant =
            Merchant(
                id = UUID.randomUUID(),
                name = "Netflix",
                domain = "netflix.com",
                sector = "streaming",
                isSystem = false,
                accountId = accountId,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val existingRule =
            TransactionRule(
                id = UUID.randomUUID(),
                accountId = accountId,
                categoryId = UUID.randomUUID(),
                merchantId = UUID.randomUUID(),
                pattern = "netflix",
                patternType = PatternType.CONTAINS,
                applicableTo = CategoryApplicableTo.ANY,
                priority = 60,
                isImplicit = true,
                createdAt = Instant.now().minusSeconds(60),
                updatedAt = Instant.now().minusSeconds(60),
            )

        whenever(merchantRepository.save(any())).thenReturn(merchant)
        doThrow(DuplicateTransactionRuleException("netflix", PatternType.CONTAINS))
            .whenever(ruleService)
            .create(any(), eq(accountId), anyOrNull())
        whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(listOf(existingRule))

        service.create("Netflix", "https://netflix.com", "streaming", accountId)

        verify(ruleRepository, never()).save(any())
        verify(merchantLogoHydrationService).hydrateForDomainAsync("netflix.com")
    }

    @Test
    fun `create rejects invalid website`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            service.create("Bad", "notawebsite", null, accountId)
        }
    }

    @Test
    fun `create rejects name over 100 chars`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            service.create("A".repeat(101), null, null, accountId)
        }
    }

    @Test
    fun `create trims and validates name length`() {
        val merchant =
            Merchant(
                id = UUID.randomUUID(),
                name = "A".repeat(100),
                accountId = accountId,
            )
        whenever(merchantRepository.save(any())).thenReturn(merchant)

        val created = service.create(" ${"A".repeat(100)} ", null, null, accountId)

        assertThat(created.name).hasSize(100)
    }

    @Test
    fun `search delegates to repository with bounded page size`() {
        val page =
            MerchantSearchPage(
                items = emptyList(),
                totalCount = 0,
                hasNextPage = false,
                hasPreviousPage = false,
                startCursor = null,
                endCursor = null,
            )
        whenever(merchantRepository.searchForAccount(accountId, "uber", null, 50)).thenReturn(page)

        val result = service.search(accountId, "uber", null, 999)

        assertThat(result.totalCount).isZero()
        verify(merchantRepository).searchForAccount(accountId, "uber", null, 50)
    }
}
