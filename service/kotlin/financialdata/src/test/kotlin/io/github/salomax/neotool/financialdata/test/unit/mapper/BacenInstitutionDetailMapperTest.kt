package io.github.salomax.neotool.financialdata.test.unit.mapper

import io.github.salomax.neotool.financialdata.domain.BacenControlType
import io.github.salomax.neotool.financialdata.domain.BacenFinanceInstitution
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionBankType
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.github.salomax.neotool.financialdata.domain.QuarterReports
import io.github.salomax.neotool.financialdata.domain.QuarterValue
import io.github.salomax.neotool.financialdata.domain.ReportAccount
import io.github.salomax.neotool.financialdata.domain.ReportDescriptor
import io.github.salomax.neotool.financialdata.domain.ReportDetails
import io.github.salomax.neotool.financialdata.domain.ReportsAvailability
import io.github.salomax.neotool.financialdata.graphql.mapper.BacenInstitutionDetailMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class BacenInstitutionDetailMapperTest {
    private val mapper = BacenInstitutionDetailMapper()

    @Test
    fun `should map finance institution to dto`() {
        val assetId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val institution =
            BacenFinanceInstitution(
                id = "033",
                name = "Banco Santander",
                legalName = "Banco Santander (Brasil) S.A.",
                tradeName = "Santander",
                assetId = assetId,
                profit = BigDecimal("10.5"),
                equity = BigDecimal("20.25"),
                yoyEquityGrowth = BigDecimal("3.2"),
                basileiaIndex = BigDecimal("11.1"),
                institutionType = BacenInstitutionType.FINANCIAL_GROUP,
                tipoConsolidadoBancario = BacenInstitutionBankType.B1,
                quarter = "202403",
                website = "https://example.com",
                socialNetworks = listOf("https://twitter.com/example"),
                mainActivity = "Banking",
                address = "Sao Paulo, SP",
                founded = "01/01/1900",
                controlType = BacenControlType.PRIVATE_DOMESTIC,
                institutionBankType = BacenInstitutionBankType.B2,
                mobilizacaoIndex = BigDecimal("4.4"),
                totalAssets = BigDecimal("999.9"),
                funding = BigDecimal("500.0"),
                loanPortfolio = BigDecimal("400.0"),
                rwaReferenceEquity = BigDecimal("123.0"),
            )

        val dto = mapper.toBacenFinanceInstitutionDTO(institution)

        assertThat(dto.id).isEqualTo("033")
        assertThat(dto.assetId).isEqualTo(assetId.toString())
        assertThat(dto.institutionType).isEqualTo(BacenInstitutionType.FINANCIAL_GROUP)
        assertThat(dto.controlType).isEqualTo(BacenControlType.PRIVATE_DOMESTIC)
        assertThat(dto.rwaReferenceEquity).isEqualTo(123.0)
    }

    @Test
    fun `should map reports availability to dto`() {
        val availability =
            ReportsAvailability(
                quarters =
                    listOf(
                        QuarterReports(
                            quarter = "202403",
                            reports =
                                listOf(
                                    ReportDescriptor(
                                        reportId = "R1",
                                        name = "Relatorio 1",
                                        group = "G1",
                                        accountId = "A1",
                                        accountName = "Conta 1",
                                    ),
                                ),
                        ),
                    ),
            )

        val dto = mapper.toReportsAvailabilityDTO(availability)

        assertThat(dto.quarters).hasSize(1)
        assertThat(dto.quarters[0].quarter).isEqualTo("202403")
        assertThat(dto.quarters[0].reports[0].reportId).isEqualTo("R1")
        assertThat(dto.quarters[0].reports[0].accountName).isEqualTo("Conta 1")
    }

    @Test
    fun `toBacenFinanceInstitutionDTO handles null optional fields`() {
        val institution =
            BacenFinanceInstitution(
                id = "001",
                name = "A",
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
            )

        val dto = mapper.toBacenFinanceInstitutionDTO(institution)

        assertThat(dto.id).isEqualTo("001")
        assertThat(dto.name).isEqualTo("A")
        assertThat(dto.legalName).isNull()
        assertThat(dto.assetId).isNull()
        assertThat(dto.profit).isNull()
        assertThat(dto.controlType).isNull()
    }

    @Test
    fun `toReportsAvailabilityDTO handles empty quarters`() {
        val availability = ReportsAvailability(quarters = emptyList())

        val dto = mapper.toReportsAvailabilityDTO(availability)

        assertThat(dto.quarters).isEmpty()
    }

    @Test
    fun `toQuarterReportsDTO handles empty reports`() {
        val quarterReports = QuarterReports(quarter = "202403", reports = emptyList())

        val dto = mapper.toQuarterReportsDTO(quarterReports)

        assertThat(dto.quarter).isEqualTo("202403")
        assertThat(dto.reports).isEmpty()
    }

    @Test
    fun `toReportDetailsDTO handles empty accounts`() {
        val details = ReportDetails(reportId = "R1", name = "Report", accounts = emptyList())

        val dto = mapper.toReportDetailsDTO(details)

        assertThat(dto.reportId).isEqualTo("R1")
        assertThat(dto.accounts).isEmpty()
    }

    @Test
    fun `toReportAccountDTO handles null group and accountId`() {
        val account =
            ReportAccount(
                group = null,
                accountId = null,
                accountName = "Some",
                valuesByQuarter = emptyList(),
            )

        val dto = mapper.toReportAccountDTO(account)

        assertThat(dto.group).isNull()
        assertThat(dto.accountId).isNull()
        assertThat(dto.accountName).isEqualTo("Some")
    }

    @Test
    fun `toQuarterValueDTO handles null value`() {
        val value = QuarterValue(quarter = "202401", value = null)

        val dto = mapper.toQuarterValueDTO(value)

        assertThat(dto.quarter).isEqualTo("202401")
        assertThat(dto.value).isNull()
    }
}
