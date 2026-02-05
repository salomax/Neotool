package io.github.salomax.neotool.financialdata.test.unit.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.domain.Indicator
import io.github.salomax.neotool.financialdata.domain.IndicatorVariation
import io.github.salomax.neotool.financialdata.graphql.resolver.IndicatorResolver
import io.github.salomax.neotool.financialdata.service.IndicatorService
import io.github.salomax.neotool.financialdata.service.IndicatorValueService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class IndicatorResolverTest {
    private val indicatorService: IndicatorService = mock()
    private val indicatorValueService: IndicatorValueService = mock()
    private lateinit var resolver: IndicatorResolver

    @BeforeEach
    fun setUp() {
        resolver = IndicatorResolver(indicatorService, indicatorValueService)
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

    @Test
    fun `economicIndicators filters by label`() {
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
        whenever(env.getArgument<String?>("label")).thenReturn("OTHER")

        val result = resolver.economicIndicators(env)
        assertThat(result).isEmpty()

        whenever(env.getArgument<String?>("label")).thenReturn("IPCA")
        val result2 = resolver.economicIndicators(env)
        assertThat(result2).hasSize(1)
    }

    @Test
    fun `variations filters by label`() {
        val variation1 =
            io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorVariationDTO(
                id = UUID.randomUUID(),
                label = "VAR1",
                indicatorLabel = "IND",
                unit = "%",
                isDefault = true,
                incrementalMonths = null,
                incrementalYears = null,
                incrementalDays = null,
            )
        val variation2 =
            io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorVariationDTO(
                id = UUID.randomUUID(),
                label = "VAR2",
                indicatorLabel = "IND",
                unit = "%",
                isDefault = false,
                incrementalMonths = null,
                incrementalYears = null,
                incrementalDays = null,
            )
        val dto =
            io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorDTO(
                id = UUID.randomUUID(),
                countryCode = "BR",
                label = "IND",
                issuer = "SRC",
                description = null,
                frequency = "M",
                unit = "%",
                isActive = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                variations = listOf(variation1, variation2),
            )

        val env: DataFetchingEnvironment = mock()
        whenever(
            env.getSource<io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorDTO>(),
        ).thenReturn(dto)

        whenever(env.getArgument<String?>("label")).thenReturn("VAR1")
        val result = resolver.variations(env)
        assertThat(result).hasSize(1)
        assertThat(result.first().label).isEqualTo("VAR1")

        whenever(env.getArgument<String?>("label")).thenReturn(null)
        val resultAll = resolver.variations(env)
        assertThat(resultAll).hasSize(2)
    }
}
