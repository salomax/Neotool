package io.github.salomax.neotool.financialdata.test.unit.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.domain.BacenFinanceInstitution
import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.domain.QuarterReports
import io.github.salomax.neotool.financialdata.domain.ReportDescriptor
import io.github.salomax.neotool.financialdata.domain.ReportDetails
import io.github.salomax.neotool.financialdata.domain.ReportsAvailability
import io.github.salomax.neotool.financialdata.graphql.dto.BacenFinanceInstitutionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportDetailsDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportsAvailabilityDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.BacenInstitutionDetailMapper
import io.github.salomax.neotool.financialdata.graphql.resolver.BacenInstitutionDetailResolver
import io.github.salomax.neotool.financialdata.service.BacenInstitutionDetailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BacenInstitutionDetailResolverTest {
    private val institutionDetailService: BacenInstitutionDetailService = mock()
    private val mapper: BacenInstitutionDetailMapper = mock()
    private lateinit var resolver: BacenInstitutionDetailResolver

    @BeforeEach
    fun setUp() {
        resolver = BacenInstitutionDetailResolver(institutionDetailService, mapper)
    }

    @Test
    fun `bacenFinanceInstitution returns DTO when found`() {
        val institution = bacenFinanceInstitution("001", "Banco Test")
        whenever(institutionDetailService.getBacenFinanceInstitution(eq("001"), eq(null)))
            .thenReturn(institution)
        val expectedDto = bacenFinanceInstitutionDTO("001", "Banco Test")
        whenever(mapper.toBacenFinanceInstitutionDTO(any())).thenReturn(expectedDto)

        val result = resolver.bacenFinanceInstitution("001", null)

        assertThat(result).isEqualTo(expectedDto)
    }

    @Test
    fun `bacenFinanceInstitution returns null when not found`() {
        whenever(institutionDetailService.getBacenFinanceInstitution(any(), any()))
            .thenReturn(null)

        val result = resolver.bacenFinanceInstitution("999", null)

        assertThat(result).isNull()
    }

    @Test
    fun `reportsSummary extracts institution from environment`() {
        val env: DataFetchingEnvironment = mock()
        val institutionDto = bacenFinanceInstitutionDTO("001", "A")
        val rangeMap = mapOf("from" to "202401", "to" to "202403")
        whenever(env.getSource<Any?>()).thenReturn(institutionDto)
        whenever(env.getArgument<Any?>("range")).thenReturn(rangeMap)

        val availability =
            ReportsAvailability(
                quarters =
                    listOf(
                        QuarterReports(
                            "202403",
                            listOf(
                                ReportDescriptor(
                                    "R1",
                                    "Report 1",
                                    null,
                                    null,
                                    null,
                                ),
                            ),
                        ),
                    ),
            )
        whenever(
            institutionDetailService.getReportsSummary(
                eq("001"),
                eq(QuarterRange(from = "202401", to = "202403")),
            ),
        ).thenReturn(availability)
        val expectedDto = ReportsAvailabilityDTO(quarters = emptyList())
        whenever(mapper.toReportsAvailabilityDTO(any())).thenReturn(expectedDto)

        val result = resolver.reportsSummary(env)

        assertThat(result).isEqualTo(expectedDto)
    }

    @Test
    fun `reportsSummary throws when institution source is null`() {
        val env: DataFetchingEnvironment = mock()
        whenever(env.getSource<Any?>()).thenReturn(null)

        val ex =
            assertThrows(IllegalStateException::class.java) {
                resolver.reportsSummary(env)
            }
        assertThat(ex.message).isEqualTo("Institution source is required")
    }

    @Test
    fun `reportsSummary throws when range parameter is missing`() {
        val env: DataFetchingEnvironment = mock()
        whenever(env.getSource<Any?>()).thenReturn(bacenFinanceInstitutionDTO("001", "A"))
        whenever(env.getArgument<Any?>("range")).thenReturn(null)

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.reportsSummary(env)
            }
        assertThat(ex.message).isEqualTo("range parameter is required")
    }

    @Test
    fun `reportsSummary parses QuarterRange correctly`() {
        val env: DataFetchingEnvironment = mock()
        whenever(env.getSource<Any?>()).thenReturn(bacenFinanceInstitutionDTO("001", "A"))
        whenever(env.getArgument<Any?>("range")).thenReturn(mapOf("last" to 4))

        whenever(
            institutionDetailService.getReportsSummary(eq("001"), eq(QuarterRange(last = 4))),
        ).thenReturn(ReportsAvailability(quarters = emptyList()))
        whenever(mapper.toReportsAvailabilityDTO(any())).thenReturn(ReportsAvailabilityDTO(quarters = emptyList()))

        val result = resolver.reportsSummary(env)

        assertThat(result.quarters).isEmpty()
    }

    @Test
    fun `reportDetails extracts institution and reportId from environment`() {
        val env: DataFetchingEnvironment = mock()
        whenever(env.getSource<Any?>()).thenReturn(bacenFinanceInstitutionDTO("001", "A"))
        whenever(env.getArgument<Any?>("reportId")).thenReturn("R1")
        whenever(env.getArgument<Any?>("range")).thenReturn(mapOf("from" to "202401", "to" to "202403"))

        val details = ReportDetails("R1", "Report 1", emptyList())
        whenever(
            institutionDetailService.getReportDetails(
                eq("001"),
                eq("R1"),
                eq(QuarterRange(from = "202401", to = "202403")),
            ),
        ).thenReturn(details)
        val expectedDto = ReportDetailsDTO("R1", "Report 1", emptyList())
        whenever(mapper.toReportDetailsDTO(any())).thenReturn(expectedDto)

        val result = resolver.reportDetails(env)

        assertThat(result).isEqualTo(expectedDto)
    }

    @Test
    fun `reportDetails throws when reportId is missing`() {
        val env: DataFetchingEnvironment = mock()
        whenever(env.getSource<Any?>()).thenReturn(bacenFinanceInstitutionDTO("001", "A"))
        whenever(env.getArgument<Any?>("reportId")).thenReturn(null)
        whenever(env.getArgument<Any?>("range")).thenReturn(mapOf("last" to 4))

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.reportDetails(env)
            }
        assertThat(ex.message).isEqualTo("reportId parameter is required")
    }

    @Test
    fun `reportDetails throws when report not found`() {
        val env: DataFetchingEnvironment = mock()
        whenever(env.getSource<Any?>()).thenReturn(bacenFinanceInstitutionDTO("001", "A"))
        whenever(env.getArgument<Any?>("reportId")).thenReturn("R1")
        whenever(env.getArgument<Any?>("range")).thenReturn(mapOf("last" to 4))

        whenever(
            institutionDetailService.getReportDetails(
                eq("001"),
                eq("R1"),
                any(),
            ),
        ).thenReturn(null)

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.reportDetails(env)
            }
        assertThat(ex.message).isEqualTo("Report not found: R1")
    }

    private fun bacenFinanceInstitution(
        id: String,
        name: String,
    ): BacenFinanceInstitution =
        BacenFinanceInstitution(
            id = id,
            name = name,
            legalName = null,
            tradeName = null,
            assetId = null,
            profit = null,
            equity = null,
            yoyEquityGrowth = null,
            basileiaIndex = null,
            institutionType = null,
            tipoConsolidadoBancario = null,
            quarter = null,
            website = null,
            socialNetworks = null,
            mainActivity = null,
            address = null,
            founded = null,
            controlType = null,
            institutionBankType = null,
            mobilizacaoIndex = null,
            totalAssets = null,
            funding = null,
            loanPortfolio = null,
            rwaReferenceEquity = null,
            updatedAt = null,
            dataRestriction =
                io.github.salomax.neotool.financialdata.domain.DataRestriction(
                    false,
                    null,
                ),
        )

    private fun bacenFinanceInstitutionDTO(
        id: String,
        name: String,
    ): BacenFinanceInstitutionDTO =
        BacenFinanceInstitutionDTO(
            id = id,
            name = name,
            legalName = null,
            tradeName = null,
            assetId = null,
            profit = null,
            equity = null,
            yoyEquityGrowth = null,
            basileiaIndex = null,
            institutionType = null,
            tipoConsolidadoBancario = null,
            quarter = null,
            website = null,
            socialNetworks = null,
            mainActivity = null,
            address = null,
            founded = null,
            controlType = null,
            institutionBankType = null,
            mobilizacaoIndex = null,
            totalAssets = null,
            funding = null,
            loanPortfolio = null,
            rwaReferenceEquity = null,
            updatedAt = null,
            dataRestriction = null,
        )
}
