package io.github.salomax.neotool.assistant.test.unit.service

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.assistant.entity.UsageBudgetEntity
import io.github.salomax.neotool.assistant.observability.AssistantMetrics
import io.github.salomax.neotool.assistant.repository.UsageBudgetRepository
import io.github.salomax.neotool.assistant.service.UsageBudgetService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class UsageBudgetServiceTest {
    private lateinit var usageBudgetRepository: UsageBudgetRepository
    private lateinit var assistantMetrics: AssistantMetrics
    private lateinit var service: UsageBudgetService

    @BeforeEach
    fun setUp() {
        usageBudgetRepository = mock()
        assistantMetrics = mock()
        service = UsageBudgetService(usageBudgetRepository, assistantMetrics)
    }

    @Test
    fun `setBudget creates new budget when no existing row exists`() {
        val scopeId = UUID.randomUUID()
        whenever(
            usageBudgetRepository.findByScopeAndScopeIdAndPeriod(BudgetScope.ACCOUNT, scopeId, BudgetPeriod.MONTHLY),
        )
            .thenReturn(null)
        whenever(usageBudgetRepository.save(any())).thenAnswer {
                invocation ->
            invocation.arguments[0] as UsageBudgetEntity
        }

        val saved =
            service.setBudget(
                scope = BudgetScope.ACCOUNT,
                scopeId = scopeId,
                period = BudgetPeriod.MONTHLY,
                limitAmount = BigDecimal("100.00"),
                alertThreshold = BigDecimal("0.80"),
            )

        assertThat(saved.scope).isEqualTo(BudgetScope.ACCOUNT)
        assertThat(saved.scopeId).isEqualTo(scopeId)
        assertThat(saved.period).isEqualTo(BudgetPeriod.MONTHLY)
        assertThat(saved.limitAmount).isEqualByComparingTo("100.000000")
        assertThat(saved.alertThreshold).isEqualByComparingTo("0.80")
        verify(usageBudgetRepository, never()).update(any())
    }

    @Test
    fun `checkBudget denies when projected usage exceeds limit`() {
        val scopeId = UUID.randomUUID()
        whenever(usageBudgetRepository.findByScopeAndScopeId(BudgetScope.ACCOUNT, scopeId)).thenReturn(
            listOf(
                UsageBudgetEntity(
                    scope = BudgetScope.ACCOUNT,
                    scopeId = scopeId,
                    period = BudgetPeriod.MONTHLY,
                    limitAmount = BigDecimal("100.000000"),
                    currentUsage = BigDecimal("95.000000"),
                    alertThreshold = BigDecimal("0.80"),
                ),
            ),
        )

        val result =
            service.checkBudget(
                scope = BudgetScope.ACCOUNT,
                scopeId = scopeId,
                estimatedCost = BigDecimal("10.000000"),
            )

        assertThat(result.allowed).isFalse()
        assertThat(result.checkedAnyBudget).isTrue()
        assertThat(result.percentUsed).isEqualByComparingTo("1.050000")
    }

    @Test
    fun `recordCost updates usage and emits alert metric when threshold is crossed`() {
        val scopeId = UUID.randomUUID()
        val budget =
            UsageBudgetEntity(
                scope = BudgetScope.FEATURE,
                scopeId = scopeId,
                period = BudgetPeriod.DAILY,
                limitAmount = BigDecimal("100.000000"),
                currentUsage = BigDecimal("70.000000"),
                alertThreshold = BigDecimal("0.80"),
            )

        whenever(usageBudgetRepository.findByScopeAndScopeId(BudgetScope.FEATURE, scopeId)).thenReturn(listOf(budget))
        whenever(usageBudgetRepository.update(any())).thenAnswer {
                invocation ->
            invocation.arguments[0] as UsageBudgetEntity
        }

        val updated =
            service.recordCost(
                scope = BudgetScope.FEATURE,
                scopeId = scopeId,
                cost = BigDecimal("15.000000"),
            )

        assertThat(updated).hasSize(1)
        assertThat(updated.first().currentUsage).isEqualByComparingTo("85.000000")
        verify(assistantMetrics).incrementBudgetAlert(eq("FEATURE"))
    }
}
