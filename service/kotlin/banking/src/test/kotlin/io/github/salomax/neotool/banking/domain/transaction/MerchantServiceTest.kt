package io.github.salomax.neotool.banking.domain.transaction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
    private lateinit var service: MerchantService

    private val accountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        merchantRepository = mock()
        ruleService = mock()
        ruleRepository = mock()
        service = MerchantService(merchantRepository, ruleService, ruleRepository)
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
            .create(any(), eq(accountId))
        whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(listOf(existingRule))
        whenever(ruleRepository.save(any())).thenAnswer { it.arguments[0] as TransactionRule }

        val created = service.create("Netflix", "netflix.com", "streaming", accountId)

        assertThat(created.id).isEqualTo(merchantId)
        val captor = argumentCaptor<TransactionRule>()
        verify(ruleRepository).save(captor.capture())
        assertThat(captor.firstValue.id).isEqualTo(existingRule.id)
        assertThat(captor.firstValue.merchantId).isEqualTo(merchantId)
        assertThat(captor.firstValue.categoryId).isEqualTo(existingRule.categoryId)
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
            .create(any(), eq(accountId))
        whenever(ruleRepository.findUserRulesForAccount(accountId)).thenReturn(listOf(existingRule))

        service.create("Netflix", "netflix.com", "streaming", accountId)

        verify(ruleRepository, never()).save(any())
    }
}
