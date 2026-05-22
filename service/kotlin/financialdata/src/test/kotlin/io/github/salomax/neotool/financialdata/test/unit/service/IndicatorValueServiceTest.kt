package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.domain.Indicator
import io.github.salomax.neotool.financialdata.domain.IndicatorVariation
import io.github.salomax.neotool.financialdata.repo.IndicatorSeriesData
import io.github.salomax.neotool.financialdata.repo.IndicatorValuePoint
import io.github.salomax.neotool.financialdata.repo.IndicatorValueRepositoryCustom
import io.github.salomax.neotool.financialdata.service.IndicatorValueService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class IndicatorValueServiceTest {
    private val repository: IndicatorValueRepositoryCustom = mock()
    private lateinit var service: IndicatorValueService

    @BeforeEach
    fun setUp() {
        service = IndicatorValueService(repository)
    }

    @Test
    fun `getSeries throws when indicator is blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.getSeries(
                indicator = "  ",
                variation = "MONTHLY",
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 12, 31),
                limit = 10,
            )
        }
    }

    @Test
    fun `getSeries throws when variation is blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.getSeries(
                indicator = "IPCA",
                variation = "",
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 12, 31),
                limit = 10,
            )
        }
    }

    @Test
    fun `getSeries throws when indicator exceeds 100 chars`() {
        val longIndicator = "a".repeat(101)

        assertThrows(IllegalArgumentException::class.java) {
            service.getSeries(
                indicator = longIndicator,
                variation = "MONTHLY",
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 12, 31),
                limit = 10,
            )
        }
    }

    @Test
    fun `getSeries throws when limit is 0`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.getSeries(
                indicator = "IPCA",
                variation = "MONTHLY",
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 12, 31),
                limit = 0,
            )
        }
    }

    @Test
    fun `getSeries throws when limit is 101`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.getSeries(
                indicator = "IPCA",
                variation = "MONTHLY",
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 12, 31),
                limit = 101,
            )
        }
    }

    @Test
    fun `getSeries returns null when repository returns no matching key`() {
        whenever(
            repository.findSeriesGrouped(any(), any(), any(), any(), any()),
        ).thenReturn(emptyMap())

        val result =
            service.getSeries(
                indicator = "IPCA",
                variation = "MONTHLY",
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 12, 31),
                limit = 10,
            )

        assertThat(result).isNull()
    }

    @Test
    fun `getSeries returns DTO when repository has data`() {
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 12, 31)
        whenever(
            repository.findSeriesGrouped(eq("IPCA"), eq("MONTHLY"), eq(from), eq(to), eq(10)),
        ).thenReturn(
            mapOf(
                ("IPCA" to "MONTHLY") to
                    IndicatorSeriesData(
                        unit = "%",
                        points =
                            listOf(
                                IndicatorValuePoint(
                                    referenceDate = LocalDate.of(2024, 1, 31),
                                    value = BigDecimal("0.52"),
                                ),
                            ),
                    ),
            ),
        )

        val result = service.getSeries("IPCA", "MONTHLY", from, to, 10)

        assertThat(result).isNotNull
        assertThat(result!!.indicator).isEqualTo("IPCA")
        assertThat(result.variation).isEqualTo("MONTHLY")
        assertThat(result.unit).isEqualTo("%")
        assertThat(result.values).hasSize(1)
    }

    @Test
    fun `getLatest returns null when no data exists`() {
        whenever(
            repository.findSeriesGrouped(any(), any(), any(), any(), any()),
        ).thenReturn(emptyMap())

        val result = service.getLatest("IPCA", "MONTHLY")

        assertThat(result).isNull()
    }

    @Test
    fun `getLatest returns last point from series`() {
        whenever(
            repository.findSeriesGrouped(any(), any(), any(), any(), eq(1)),
        ).thenReturn(
            mapOf(
                ("IPCA" to "MONTHLY") to
                    IndicatorSeriesData(
                        unit = "%",
                        points =
                            listOf(
                                IndicatorValuePoint(LocalDate.of(2024, 1, 31), BigDecimal("0.50")),
                                IndicatorValuePoint(LocalDate.of(2024, 2, 29), BigDecimal("0.60")),
                            ),
                    ),
            ),
        )

        val result = service.getLatest("IPCA", "MONTHLY")

        assertThat(result).isNotNull
        assertThat(result!!.referenceDate).isEqualTo(LocalDate.of(2024, 2, 29))
        assertThat(result.value).isEqualByComparingTo(BigDecimal("0.60"))
    }

    @Test
    fun `getSeriesGrouped delegates to repository with null indicator and variation`() {
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 12, 31)
        whenever(
            repository.findSeriesGrouped(eq(null), eq(null), eq(from), eq(to), eq(50)),
        ).thenReturn(emptyMap())

        service.getSeriesGrouped(from, to, 50)

        verify(repository).findSeriesGrouped(eq(null), eq(null), eq(from), eq(to), eq(50))
    }

    @Test
    fun `getAllLatest includes null latest for variations with no data`() {
        val indicator =
            Indicator(
                id = UUID.randomUUID(),
                countryCode = "BR",
                label = "IPCA",
                issuer = "IBGE",
                description = null,
                frequency = "M",
                unit = "%",
                isActive = true,
            )
        val variation =
            IndicatorVariation(
                id = UUID.randomUUID(),
                indicatorId = indicator.id!!,
                label = "MONTHLY",
                unit = "%",
                isDefault = true,
            )
        whenever(repository.findSeriesGrouped(eq(null), eq(null), any(), any(), eq(1))).thenReturn(emptyMap())

        val result = service.getAllLatest(mapOf(indicator to listOf(variation)))

        assertThat(result).hasSize(1)
        assertThat(result[0].indicator).isEqualTo("IPCA")
        assertThat(result[0].variation).isEqualTo("MONTHLY")
        assertThat(result[0].latest).isNull()
    }

    @Test
    fun `getAllLatest populates latest when data exists`() {
        val indicator =
            Indicator(
                id = UUID.randomUUID(),
                countryCode = "BR",
                label = "IPCA",
                issuer = "IBGE",
                description = null,
                frequency = "M",
                unit = "%",
                isActive = true,
            )
        val variation =
            IndicatorVariation(
                id = UUID.randomUUID(),
                indicatorId = indicator.id!!,
                label = "MONTHLY",
                unit = "%",
                isDefault = true,
            )
        whenever(repository.findSeriesGrouped(eq(null), eq(null), any(), any(), eq(1))).thenReturn(
            mapOf(
                ("IPCA" to "MONTHLY") to
                    IndicatorSeriesData(
                        unit = "%",
                        points =
                            listOf(
                                IndicatorValuePoint(LocalDate.of(2024, 3, 31), BigDecimal("0.71")),
                            ),
                    ),
            ),
        )

        val result = service.getAllLatest(mapOf(indicator to listOf(variation)))

        assertThat(result).hasSize(1)
        assertThat(result[0].latest).isNotNull
        assertThat(result[0].latest!!.value).isEqualByComparingTo(BigDecimal("0.71"))
    }
}
