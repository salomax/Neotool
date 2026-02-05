package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.domain.CanonicalAccountNames
import io.github.salomax.neotool.financialdata.domain.DataRestriction
import io.github.salomax.neotool.financialdata.domain.DataRestrictionReason
import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionDetailRepositoryCustom
import io.github.salomax.neotool.financialdata.repo.result.BacenInstitutionReportDataResult
import io.github.salomax.neotool.financialdata.repo.result.FinancialInstitutionDataResult
import io.github.salomax.neotool.financialdata.repo.result.ReportDescriptorResult
import io.github.salomax.neotool.financialdata.repo.result.ReportValueResult
import io.github.salomax.neotool.financialdata.service.BacenInstitutionDetailService
import io.github.salomax.neotool.financialdata.service.InstitutionDataRestrictionService
import io.github.salomax.neotool.financialdata.service.QuarterService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class BacenInstitutionDetailServiceTest {
    private val institutionDetailRepository: BacenInstitutionDetailRepositoryCustom = mock()
    private val quarterService: QuarterService = mock()
    private val restrictionService: InstitutionDataRestrictionService = mock()
    private lateinit var service: BacenInstitutionDetailService

    @BeforeEach
    fun setUp() {
        whenever(restrictionService.isRestricted(any())).thenReturn(DataRestriction.NOT_RESTRICTED)
        service = BacenInstitutionDetailService(institutionDetailRepository, quarterService, restrictionService)
    }

    @Test
    fun `getBacenFinanceInstitution returns institution when found`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(institutionDetailRepository.getFinancialInstitutionData(eq("001"), eq("202403")))
            .thenReturn(financialData("Banco Test"))
        whenever(institutionDetailRepository.getBacenInstitutionReportData(eq("001"), eq("202403")))
            .thenReturn(
                listOf(
                    reportData(CanonicalAccountNames.LUCRO_LIQUIDO, "100.50"),
                    reportData(CanonicalAccountNames.PATRIMONIO_LIQUIDO, "500.75"),
                ),
            )

        val result = service.getBacenFinanceInstitution("001", null)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo("001")
        assertThat(result.name).isEqualTo("Banco Test")
        assertThat(result.profit).isEqualByComparingTo(BigDecimal("100.50"))
        assertThat(result.equity).isEqualByComparingTo(BigDecimal("500.75"))
    }

    @Test
    fun `getBacenFinanceInstitution returns metadata only when restricted`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(institutionDetailRepository.getFinancialInstitutionData(eq("001"), eq("202403")))
            .thenReturn(financialData("Banco Restricted"))
        whenever(restrictionService.isRestricted(eq("001")))
            .thenReturn(DataRestriction(isRestricted = true, reason = DataRestrictionReason.INSTITUTION_REQUEST))

        val result = service.getBacenFinanceInstitution("001", null)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo("001")
        assertThat(result.name).isEqualTo("Banco Restricted")
        assertThat(result.dataRestriction.isRestricted).isTrue()
        assertThat(result.profit).isNull()
        assertThat(result.equity).isNull()
        assertThat(result.basileiaIndex).isNull()
        assertThat(result.rwaReferenceEquity).isNull()
    }

    @Test
    fun `getBacenFinanceInstitution returns null when institution not found`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(institutionDetailRepository.getFinancialInstitutionData(any(), any()))
            .thenReturn(null)

        val result = service.getBacenFinanceInstitution("999", null)

        assertThat(result).isNull()
    }

    @Test
    fun `getBacenFinanceInstitution returns null when no quarter available`() {
        whenever(quarterService.getLatestQuarter()).thenReturn(null)

        val result = service.getBacenFinanceInstitution("001", null)

        assertThat(result).isNull()
    }

    @Test
    fun `getBacenFinanceInstitution extracts financial metrics from report data`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(institutionDetailRepository.getFinancialInstitutionData(any(), any()))
            .thenReturn(financialData("A"))
        whenever(institutionDetailRepository.getBacenInstitutionReportData(any(), any()))
            .thenReturn(
                listOf(
                    reportData(CanonicalAccountNames.LUCRO_LIQUIDO, "10"),
                    reportData(CanonicalAccountNames.PATRIMONIO_LIQUIDO, "20"),
                    reportData(CanonicalAccountNames.INDICE_DE_BASILEIA, "12.5"),
                    reportData(CanonicalAccountNames.INDICE_DE_IMOBILIZACAO, "4.4"),
                    reportData(CanonicalAccountNames.ATIVO_TOTAL, "1000"),
                    reportData(CanonicalAccountNames.CAPTACOES, "500"),
                    reportData(CanonicalAccountNames.CARTEIRA_DE_CREDITO, "400"),
                    reportData(CanonicalAccountNames.PATRIMONIO_DE_REFERENCIA, "123"),
                ),
            )

        val result = service.getBacenFinanceInstitution("001", null)

        assertThat(result).isNotNull
        assertThat(result!!.profit).isEqualByComparingTo(BigDecimal("10"))
        assertThat(result.equity).isEqualByComparingTo(BigDecimal("20"))
        assertThat(result.basileiaIndex).isEqualByComparingTo(BigDecimal("12.5"))
        assertThat(result.mobilizacaoIndex).isEqualByComparingTo(BigDecimal("4.4"))
        assertThat(result.totalAssets).isEqualByComparingTo(BigDecimal("1000"))
        assertThat(result.funding).isEqualByComparingTo(BigDecimal("500"))
        assertThat(result.loanPortfolio).isEqualByComparingTo(BigDecimal("400"))
        assertThat(result.rwaReferenceEquity).isEqualByComparingTo(BigDecimal("123"))
    }

    @Test
    fun `getBacenFinanceInstitution handles missing canonical accounts gracefully`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(institutionDetailRepository.getFinancialInstitutionData(any(), any()))
            .thenReturn(financialData("A"))
        whenever(institutionDetailRepository.getBacenInstitutionReportData(any(), any()))
            .thenReturn(listOf(reportData("SOME_OTHER_ACCOUNT", "99")))

        val result = service.getBacenFinanceInstitution("001", null)

        assertThat(result).isNotNull
        assertThat(result!!.profit).isNull()
        assertThat(result.equity).isNull()
        assertThat(result.basileiaIndex).isNull()
    }

    @Test
    fun `getBacenFinanceInstitution maps control type correctly`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(institutionDetailRepository.getBacenInstitutionReportData(any(), any()))
            .thenReturn(emptyList())

        listOf("1", "2", "3", null).forEachIndexed { i, code ->
            whenever(institutionDetailRepository.getFinancialInstitutionData(eq("id$i"), eq("202403")))
                .thenReturn(financialData("A", tipoControle = code))

            val result = service.getBacenFinanceInstitution("id$i", null)

            assertThat(result).isNotNull
            when (code) {
                "1" -> assertThat(result!!.controlType?.name).isEqualTo("PUBLIC")
                "2" -> assertThat(result!!.controlType?.name).isEqualTo("PRIVATE_DOMESTIC")
                "3" -> assertThat(result!!.controlType?.name).isEqualTo("PRIVATE_FOREIGN_CONTROL")
                null -> assertThat(result!!.controlType).isNull()
            }
        }
    }

    @Test
    fun `getBacenFinanceInstitution maps institution bank type correctly`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(institutionDetailRepository.getBacenInstitutionReportData(any(), any()))
            .thenReturn(emptyList())

        val codes = listOf("B1", "B2", "B3S", "B3C", "B4", "N1", "N2", "N4", null)
        codes.forEachIndexed { i, code ->
            whenever(institutionDetailRepository.getFinancialInstitutionData(eq("id$i"), eq("202403")))
                .thenReturn(financialData("A", tipoConsolidadoBancario = code))

            val result = service.getBacenFinanceInstitution("id$i", null)

            assertThat(result).isNotNull
            assertThat(result!!.tipoConsolidadoBancario?.name).isEqualTo(code?.uppercase())
            assertThat(result.institutionBankType?.name).isEqualTo(code?.uppercase())
        }
    }

    @Test
    fun `getBacenFinanceInstitution maps institution type correctly`() {
        whenever(quarterService.getLatestQuarter()).thenReturn("202403")
        whenever(institutionDetailRepository.getFinancialInstitutionData(any(), any()))
            .thenReturn(financialData("A", tipoConsolidacao = "I"))
        whenever(institutionDetailRepository.getBacenInstitutionReportData(any(), any()))
            .thenReturn(emptyList())

        val result = service.getBacenFinanceInstitution("001", null)

        assertThat(result).isNotNull
        assertThat(result!!.institutionType?.name).isEqualTo("INDIVIDUAL_INSTITUTION")
    }

    @Test
    fun `getReportsSummary returns grouped reports by quarter`() {
        val range = QuarterRange(from = "202401", to = "202403")
        whenever(
            institutionDetailRepository.getReportsByQuarterRange(eq("001"), any()),
        ).thenReturn(
            listOf(
                ReportDescriptorResult("202401", "R1", "Report 1", null, null, null),
                ReportDescriptorResult("202401", "R2", "Report 2", null, null, null),
                ReportDescriptorResult("202403", "R1", "Report 1", null, null, null),
            ),
        )

        val result = service.getReportsSummary("001", range)

        assertThat(result.quarters).hasSize(2)
        assertThat(result.quarters.map { it.quarter }).containsExactlyInAnyOrder("202401", "202403")
        assertThat(result.quarters.find { it.quarter == "202401" }!!.reports).hasSize(2)
        assertThat(result.quarters.find { it.quarter == "202403" }!!.reports).hasSize(1)
    }

    @Test
    fun `getReportsSummary returns empty when no reports found`() {
        val range = QuarterRange(last = 4)
        whenever(institutionDetailRepository.getReportsByQuarterRange(any(), any()))
            .thenReturn(emptyList())

        val result = service.getReportsSummary("001", range)

        assertThat(result.quarters).isEmpty()
    }

    @Test
    fun `getReportsSummary deduplicates reports by reportId`() {
        val range = QuarterRange(last = 4)
        whenever(institutionDetailRepository.getReportsByQuarterRange(any(), any()))
            .thenReturn(
                listOf(
                    ReportDescriptorResult("202403", "R1", "Report 1", null, null, null),
                    ReportDescriptorResult("202403", "R1", "Report 1", "G", "A", "X"),
                ),
            )

        val result = service.getReportsSummary("001", range)

        assertThat(result.quarters).hasSize(1)
        assertThat(result.quarters[0].reports).hasSize(1)
        assertThat(result.quarters[0].reports[0].reportId).isEqualTo("R1")
    }

    @Test
    fun `getReportsSummary sorts quarters descending`() {
        val range = QuarterRange(last = 4)
        whenever(institutionDetailRepository.getReportsByQuarterRange(any(), any()))
            .thenReturn(
                listOf(
                    ReportDescriptorResult("202401", "R1", "R", null, null, null),
                    ReportDescriptorResult("202403", "R1", "R", null, null, null),
                    ReportDescriptorResult("202402", "R1", "R", null, null, null),
                ),
            )

        val result = service.getReportsSummary("001", range)

        assertThat(result.quarters.map { it.quarter }).containsExactly("202403", "202402", "202401")
    }

    @Test
    fun `getReportDetails returns report with accounts`() {
        val range = QuarterRange(last = 4)
        whenever(
            institutionDetailRepository.getReportDetailsByQuarterRange(
                eq("001"),
                eq("R1"),
                any(),
            ),
        ).thenReturn(
            listOf(
                ReportValueResult("202403", "R1", "Report 1", "G1", "A1", "Account 1", BigDecimal("100")),
            ),
        )

        val result = service.getReportDetails("001", "R1", range)

        assertThat(result).isNotNull
        assertThat(result!!.reportId).isEqualTo("R1")
        assertThat(result.name).isEqualTo("Report 1")
        assertThat(result.accounts).hasSize(1)
        assertThat(result.accounts[0].group).isEqualTo("G1")
        assertThat(result.accounts[0].accountId).isEqualTo("A1")
        assertThat(result.accounts[0].accountName).isEqualTo("Account 1")
        assertThat(result.accounts[0].valuesByQuarter).hasSize(1)
        assertThat(result.accounts[0].valuesByQuarter[0].quarter).isEqualTo("202403")
        assertThat(result.accounts[0].valuesByQuarter[0].value).isEqualByComparingTo(BigDecimal("100"))
    }

    @Test
    fun `getReportDetails returns null when report not found`() {
        val range = QuarterRange(last = 4)
        whenever(
            institutionDetailRepository.getReportDetailsByQuarterRange(any(), any(), any()),
        ).thenReturn(emptyList())

        val result = service.getReportDetails("001", "R99", range)

        assertThat(result).isNull()
    }

    @Test
    fun `getReportDetails groups values by account correctly`() {
        val range = QuarterRange(last = 4)
        whenever(
            institutionDetailRepository.getReportDetailsByQuarterRange(any(), any(), any()),
        ).thenReturn(
            listOf(
                ReportValueResult("202401", "R1", "R", "G1", "A1", "Acc1", BigDecimal("10")),
                ReportValueResult("202402", "R1", "R", "G1", "A1", "Acc1", BigDecimal("20")),
            ),
        )

        val result = service.getReportDetails("001", "R1", range)

        assertThat(result).isNotNull
        assertThat(result!!.accounts).hasSize(1)
        assertThat(result.accounts[0].valuesByQuarter).hasSize(2)
        assertThat(result.accounts[0].valuesByQuarter.map { it.quarter }).containsExactlyInAnyOrder("202402", "202401")
    }

    @Test
    fun `getReportDetails sorts accounts by accountId and group`() {
        val range = QuarterRange(last = 4)
        whenever(
            institutionDetailRepository.getReportDetailsByQuarterRange(any(), any(), any()),
        ).thenReturn(
            listOf(
                ReportValueResult("202403", "R1", "R", "G2", "A2", "B", BigDecimal("2")),
                ReportValueResult("202403", "R1", "R", "G1", "A1", "A", BigDecimal("1")),
                ReportValueResult("202403", "R1", "R", "G1", "A3", "C", BigDecimal("3")),
            ),
        )

        val result = service.getReportDetails("001", "R1", range)

        assertThat(result).isNotNull
        assertThat(result!!.accounts.map { it.accountId to it.group })
            .containsExactly(
                "A1" to "G1",
                "A2" to "G2",
                "A3" to "G1",
            )
    }

    @Test
    fun `getReportDetails aggregates values by quarter per account`() {
        val range = QuarterRange(last = 4)
        whenever(
            institutionDetailRepository.getReportDetailsByQuarterRange(any(), any(), any()),
        ).thenReturn(
            listOf(
                ReportValueResult("202401", "R1", "R", "G", "A", "X", BigDecimal("10")),
                ReportValueResult("202403", "R1", "R", "G", "A", "X", BigDecimal("30")),
                ReportValueResult("202402", "R1", "R", "G", "A", "X", BigDecimal("20")),
            ),
        )

        val result = service.getReportDetails("001", "R1", range)

        assertThat(result).isNotNull
        assertThat(result!!.accounts).hasSize(1)
        val qv = result.accounts[0].valuesByQuarter
        assertThat(qv.map { it.quarter }).containsExactly("202403", "202402", "202401")
        assertThat(qv.map { it.value?.toInt() }).containsExactly(30, 20, 10)
    }

    private fun financialData(
        nomeInstituicao: String,
        tipoControle: String? = null,
        tipoConsolidacao: String? = "I",
        tipoConsolidadoBancario: String? = "B1",
    ): FinancialInstitutionDataResult =
        FinancialInstitutionDataResult(
            nomeInstituicao = nomeInstituicao,
            tipoControle = tipoControle,
            tipoConsolidacao = tipoConsolidacao,
            tipoConsolidadoBancario = tipoConsolidadoBancario,
            legalName = null,
            tradeName = null,
            assetId = null,
            website = null,
            socialNetworks = null,
            mainActivity = null,
            address = null,
            founded = null,
            updatedAt = null,
        )

    private fun reportData(
        canonicalAccountName: String,
        balance: String,
    ): BacenInstitutionReportDataResult =
        BacenInstitutionReportDataResult(
            bacenCodInst = "001",
            anoMesRef = "202403",
            reportNumber = "50",
            reportName = null,
            group = null,
            account = null,
            canonicalAccountName = canonicalAccountName,
            columnName = null,
            balance = BigDecimal(balance),
        )
}
