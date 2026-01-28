package io.github.salomax.neotool.financialdata.test.unit.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.domain.Indicator
import io.github.salomax.neotool.financialdata.domain.IndicatorVariation
import io.github.salomax.neotool.financialdata.graphql.resolver.IndicatorResolver
import io.github.salomax.neotool.financialdata.service.IndicatorService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class IndicatorResolverTest {
    private val indicatorService: IndicatorService = mock()
    private lateinit var resolver: IndicatorResolver

    @BeforeEach
    fun setUp() {
        resolver = IndicatorResolver(indicatorService)
    }

    @Test
    fun `economicIndicators returns DTOs with variations`() {
        val indicator =
            Indicator(
                id = UUID.randomUUID(),
                countryCode = "BR",
                label = "IPCA",
                issuer = "IBGE",
                description = "Inflation index",
                frequency = "monthly",
                unit = "%",
                isActive = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val variation =
            IndicatorVariation(
                id = UUID.randomUUID(),
                indicatorId = indicator.id!!,
                label = "MONTHLY_CHANGE",
                unit = "%",
                isDefault = true,
                incrementalMonths = 6,
                incrementalYears = null,
                incrementalDays = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        whenever(indicatorService.getActiveIndicators()).thenReturn(mapOf(indicator to listOf(variation)))

        val env: DataFetchingEnvironment = mock()
        val result = resolver.economicIndicators(env)

        assertThat(result).hasSize(1)
        val dto = result.first()
        assertThat(dto.label).isEqualTo("IPCA")
        assertThat(dto.variations).hasSize(1)
        assertThat(dto.variations.first().label).isEqualTo("MONTHLY_CHANGE")
    }
}
