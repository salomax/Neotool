package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.domain.Indicator
import io.github.salomax.neotool.financialdata.domain.IndicatorVariation
import io.github.salomax.neotool.financialdata.repo.IndicatorReadRepository
import io.github.salomax.neotool.financialdata.service.IndicatorService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class IndicatorServiceTest {
    private val indicatorReadRepository: IndicatorReadRepository = mock()
    private lateinit var service: IndicatorService

    @BeforeEach
    fun setUp() {
        service = IndicatorService(indicatorReadRepository)
    }

    @Test
    fun `getActiveIndicators returns indicators with variations`() {
        val indicatorId = UUID.randomUUID()
        val indicator =
            Indicator(
                id = indicatorId,
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
                indicatorId = indicatorId,
                label = "MONTHLY_CHANGE",
                unit = "%",
                isDefault = true,
                incrementalMonths = 6,
                incrementalYears = null,
                incrementalDays = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        whenever(indicatorReadRepository.findActiveIndicatorsWithVariations())
            .thenReturn(mapOf(indicator to listOf(variation)))

        val result = service.getActiveIndicators()

        assertThat(result).hasSize(1)
        val entry = result.entries.first()
        assertThat(entry.key.label).isEqualTo("IPCA")
        assertThat(entry.value).hasSize(1)
        assertThat(entry.value.first().label).isEqualTo("MONTHLY_CHANGE")
    }

    @Test
    fun `getActiveIndicators returns empty map when no indicators`() {
        whenever(indicatorReadRepository.findActiveIndicatorsWithVariations()).thenReturn(emptyMap())

        val result = service.getActiveIndicators()

        assertThat(result).isEmpty()
    }
}
