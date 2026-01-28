package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.model.IndicatorEntity
import io.github.salomax.neotool.financialdata.model.IndicatorVariationEntity
import io.github.salomax.neotool.financialdata.repo.IndicatorRepository
import io.github.salomax.neotool.financialdata.repo.IndicatorVariationRepository
import io.github.salomax.neotool.financialdata.service.IndicatorService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class IndicatorServiceTest {
    private val indicatorRepository: IndicatorRepository = mock()
    private val variationRepository: IndicatorVariationRepository = mock()
    private lateinit var service: IndicatorService

    @BeforeEach
    fun setUp() {
        service = IndicatorService(indicatorRepository, variationRepository)
    }

    @Test
    fun `getActiveIndicators returns indicators with variations`() {
        val indicatorId = UUID.randomUUID()
        val indicator =
            IndicatorEntity(
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
            IndicatorVariationEntity(
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

        whenever(indicatorRepository.findByIsActiveTrueOrderByLabelAsc()).thenReturn(listOf(indicator))
        whenever(variationRepository.findByIndicatorIdIn(listOf(indicatorId))).thenReturn(listOf(variation))

        val result = service.getActiveIndicators()

        assertThat(result).hasSize(1)
        val entry = result.entries.first()
        assertThat(entry.key.label).isEqualTo("IPCA")
        assertThat(entry.value).hasSize(1)
        assertThat(entry.value.first().label).isEqualTo("MONTHLY_CHANGE")
    }

    @Test
    fun `getActiveIndicators returns empty map when no indicators`() {
        whenever(indicatorRepository.findByIsActiveTrueOrderByLabelAsc()).thenReturn(emptyList())

        val result = service.getActiveIndicators()

        assertThat(result).isEmpty()
    }
}
