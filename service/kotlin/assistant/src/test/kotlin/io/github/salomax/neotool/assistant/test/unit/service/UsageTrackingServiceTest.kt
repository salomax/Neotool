package io.github.salomax.neotool.assistant.test.unit.service

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.entity.UsageLogEntity
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.repository.FeatureUsageProjection
import io.github.salomax.neotool.assistant.repository.ModelUsageProjection
import io.github.salomax.neotool.assistant.repository.UsageLogRepository
import io.github.salomax.neotool.assistant.repository.UsageTotalsProjection
import io.github.salomax.neotool.assistant.service.CompletionResult
import io.github.salomax.neotool.assistant.service.UsageTrackingService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class UsageTrackingServiceTest {
    private lateinit var usageLogRepository: UsageLogRepository
    private lateinit var usageTrackingService: UsageTrackingService

    @BeforeEach
    fun setUp() {
        usageLogRepository = mock()
        usageTrackingService = UsageTrackingService(usageLogRepository)
    }

    @Test
    fun `recordUsage persists usage log from completion response`() {
        val accountId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val modelId = UUID.randomUUID()
        val usageLogId = UUID.randomUUID()

        val completionResult =
            CompletionResult(
                id = UUID.randomUUID(),
                requestId = requestId,
                accountId = accountId,
                providerId = providerId,
                modelId = modelId,
                model = "gpt-4o-mini",
                content = "ok",
                finishReason = "stop",
                usage = ProviderTokenUsage(promptTokens = 12, completionTokens = 8, totalTokens = 20),
                latencyMs = 100,
                cost = BigDecimal("0.001234"),
                escalate = false,
                toolCalls = emptyList(),
            )

        whenever(usageLogRepository.save(any())).thenAnswer { invocation ->
            val value = invocation.arguments[0] as UsageLogEntity
            UsageLogEntity(
                id = usageLogId,
                accountId = value.accountId,
                requestId = value.requestId,
                service = value.service,
                userId = value.userId,
                feature = value.feature,
                purpose = value.purpose,
                promptTokens = value.promptTokens,
                completionTokens = value.completionTokens,
                totalTokens = value.totalTokens,
                cost = value.cost,
                modelId = value.modelId,
                timestamp = value.timestamp,
            )
        }

        val result =
            usageTrackingService.recordUsage(
                completionResponse = completionResult,
                accountId = accountId,
                service = "assistant-api",
                feature = "generateCompletion",
                purpose = "usage_test",
            )

        assertThat(result.id).isEqualTo(usageLogId)
        assertThat(result.accountId).isEqualTo(accountId)
        assertThat(result.requestId).isEqualTo(requestId)
        assertThat(result.service).isEqualTo("assistant-api")
        assertThat(result.feature).isEqualTo("generateCompletion")
        assertThat(result.purpose).isEqualTo("usage_test")
        assertThat(result.promptTokens).isEqualTo(12)
        assertThat(result.completionTokens).isEqualTo(8)
        assertThat(result.totalTokens).isEqualTo(20)
        assertThat(result.cost).isEqualByComparingTo("0.001234")
        assertThat(result.modelId).isEqualTo(modelId)

        val captor = argumentCaptor<UsageLogEntity>()
        verify(usageLogRepository).save(captor.capture())
        val savedValue = captor.firstValue
        assertThat(savedValue.accountId).isEqualTo(accountId)
        assertThat(savedValue.requestId).isEqualTo(requestId)
    }

    @Test
    fun `getUsageSummary aggregates tokens and cost for account and period`() {
        val accountId = UUID.randomUUID()
        val model1 = UUID.randomUUID()
        val model2 = UUID.randomUUID()

        whenever(usageLogRepository.summarizeByAccountIdAndTimestampBetween(eq(accountId), any(), any())).thenReturn(
            totals(
                requests = 3,
                promptTokens = 22,
                completionTokens = 13,
                totalTokens = 35,
                cost = BigDecimal("0.006000"),
            ),
        )
        whenever(usageLogRepository.summarizeByAccountIdGroupedByModel(eq(accountId), any(), any())).thenReturn(
            listOf(
                modelUsage(
                    modelId = model1,
                    requests = 2,
                    promptTokens = 14,
                    completionTokens = 11,
                    totalTokens = 25,
                    cost = BigDecimal("0.003000"),
                ),
                modelUsage(
                    modelId = model2,
                    requests = 1,
                    promptTokens = 8,
                    completionTokens = 2,
                    totalTokens = 10,
                    cost = BigDecimal("0.003000"),
                ),
            ),
        )
        whenever(usageLogRepository.summarizeByAccountIdGroupedByFeature(eq(accountId), any(), any())).thenReturn(
            listOf(
                featureUsage(
                    feature = "generateCompletion",
                    requests = 2,
                    promptTokens = 14,
                    completionTokens = 11,
                    totalTokens = 25,
                    cost = BigDecimal("0.003000"),
                ),
                featureUsage(
                    feature = "faq",
                    requests = 1,
                    promptTokens = 8,
                    completionTokens = 2,
                    totalTokens = 10,
                    cost = BigDecimal("0.003000"),
                ),
            ),
        )

        val summary = usageTrackingService.getUsageSummary(accountId, BudgetPeriod.DAILY)

        assertThat(summary.period).isEqualTo(BudgetPeriod.DAILY)
        assertThat(summary.requests).isEqualTo(3)
        assertThat(summary.promptTokens).isEqualTo(22)
        assertThat(summary.completionTokens).isEqualTo(13)
        assertThat(summary.totalTokens).isEqualTo(35)
        assertThat(summary.cost).isEqualByComparingTo("0.006000")
        assertThat(summary.startTime).isBeforeOrEqualTo(summary.endTime)

        assertThat(summary.byModel).hasSize(2)
        assertThat(summary.byModel[0].modelId).isEqualTo(model1)
        assertThat(summary.byModel[0].totalTokens).isEqualTo(25)
        assertThat(summary.byFeature).hasSize(2)
        assertThat(summary.byFeature[0].feature).isEqualTo("generateCompletion")
        assertThat(summary.byFeature[0].totalTokens).isEqualTo(25)
    }

    @Test
    fun `getServiceUsage rejects blank service id`() {
        assertThatThrownBy {
            usageTrackingService.getServiceUsage("  ", BudgetPeriod.WEEKLY)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("serviceId")
    }

    private fun totals(
        requests: Long,
        promptTokens: Long,
        completionTokens: Long,
        totalTokens: Long,
        cost: BigDecimal,
    ): UsageTotalsProjection {
        return object : UsageTotalsProjection {
            override val requests = requests
            override val promptTokens = promptTokens
            override val completionTokens = completionTokens
            override val totalTokens = totalTokens
            override val cost = cost
        }
    }

    private fun modelUsage(
        modelId: UUID?,
        requests: Long,
        promptTokens: Long,
        completionTokens: Long,
        totalTokens: Long,
        cost: BigDecimal,
    ): ModelUsageProjection {
        return object : ModelUsageProjection {
            override val modelId = modelId
            override val requests = requests
            override val promptTokens = promptTokens
            override val completionTokens = completionTokens
            override val totalTokens = totalTokens
            override val cost = cost
        }
    }

    private fun featureUsage(
        feature: String,
        requests: Long,
        promptTokens: Long,
        completionTokens: Long,
        totalTokens: Long,
        cost: BigDecimal,
    ): FeatureUsageProjection {
        return object : FeatureUsageProjection {
            override val feature = feature
            override val requests = requests
            override val promptTokens = promptTokens
            override val completionTokens = completionTokens
            override val totalTokens = totalTokens
            override val cost = cost
        }
    }
}
