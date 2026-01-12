package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.financialdata.domain.BacenControlType
import io.github.salomax.neotool.financialdata.domain.BacenFinanceInstitution
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionBankType
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.domain.QuarterReports
import io.github.salomax.neotool.financialdata.domain.QuarterValue
import io.github.salomax.neotool.financialdata.domain.ReportDescriptor
import io.github.salomax.neotool.financialdata.domain.ReportDetails
import io.github.salomax.neotool.financialdata.domain.ReportsAvailability
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionDetailRepositoryCustom
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Service for fetching detailed Bacen institution data and reports.
 */
@Singleton
open class BacenInstitutionDetailService(
    private val institutionDetailRepository: BacenInstitutionDetailRepositoryCustom,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get enriched BacenFinanceInstitution data for a single institution.
     * Filters by quarter (ano_mes_ref). If anoMes is null, uses latest quarter globally.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @return BacenFinanceInstitution or null if not found
     */
    fun getBacenFinanceInstitution(id: String, anoMes: String? = null): BacenFinanceInstitution? {
        val result = institutionDetailRepository.getInstitutionDetail(id, anoMes) ?: return null

        // Map control type
        val controlType = mapControlType(result.tipoControle)

        // Map institution bank type
        val institutionBankType = mapInstitutionBankType(result.tipoConsolidadoBancarioCode)

        // Map institution type
        val institutionType = mapInstitutionType(
            tipoConsolidacao = result.institutionType,
            bacenCodInst = result.bacenCodInst,
            codConglomeradoPrudencial = null, // Not available in detail result
            codConglomeradoFinanceiro = null, // Not available in detail result
        )

        return BacenFinanceInstitution(
            id = result.bacenCodInst,
            name = result.nomeInstituicao ?: "Unknown",
            legalName = result.legalName,
            tradeName = result.tradeName,
            assetId = result.assetId,
            profit = result.profit,
            equity = result.equity,
            yoyEquityGrowth = result.yoyEquityGrowth,
            basileiaIndex = result.basileiaIndex,
            bacenInstitutionType = result.bacenInstitutionType,
            institutionType = institutionType,
            tipoConsolidadoBancario = result.tipoConsolidadoBancario,
            quarter = result.quarter,
            website = result.website,
            socialNetworks = result.socialNetworks,
            mainActivity = result.mainActivity,
            address = result.address,
            founded = result.founded,
            controlType = controlType,
            institutionBankType = institutionBankType,
            mobilizacaoIndex = result.mobilizacaoIndex,
            totalAssets = result.totalAssets,
            funding = result.funding,
            loanPortfolio = result.loanPortfolio,
            rwaReferenceEquity = result.rwaReferenceEquity,
        )
    }

    /**
     * Get available reports grouped by quarter for an institution.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param range Quarter range filter
     * @return ReportsAvailability with quarters and their reports
     */
    fun getReportsSummary(id: String, range: QuarterRange): ReportsAvailability {
        val reports = institutionDetailRepository.getReportsByQuarterRange(id, range)

        // Group by quarter
        val quartersMap = reports.groupBy { it.quarter }

        val quarterReports = quartersMap.map { (quarter, reports) ->
            QuarterReports(
                quarter = quarter,
                reports = reports.map { report ->
                    ReportDescriptor(
                        reportId = report.reportId,
                        name = report.name,
                        group = report.group,
                        accountNumber = report.accountNumber,
                        columnName = report.columnName,
                    )
                },
            )
        }.sortedByDescending { it.quarter } // Most recent first

        return ReportsAvailability(quarters = quarterReports)
    }

    /**
     * Get detailed report values by quarter for a specific report.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param reportId Report ID (numero_relatorio)
     * @param range Quarter range filter
     * @return ReportDetails or null if report not found
     */
    fun getReportDetails(id: String, reportId: String, range: QuarterRange): ReportDetails? {
        val values = institutionDetailRepository.getReportDetailsByQuarterRange(id, reportId, range)

        if (values.isEmpty()) {
            return null
        }

        // Use first value for metadata (all should have same report metadata)
        val first = values.first()

        val quarterValues = values.map { value ->
            QuarterValue(
                quarter = value.quarter,
                value = value.value,
            )
        }.sortedByDescending { it.quarter } // Most recent first

        return ReportDetails(
            reportId = first.reportId,
            name = first.name,
            group = first.group,
            accountNumber = first.accountNumber,
            columnName = first.columnName,
            valuesByQuarter = quarterValues,
        )
    }

    /**
     * Map control type code to enum.
     */
    private fun mapControlType(code: String?): BacenControlType? {
        return when (code) {
            "1" -> BacenControlType.PUBLIC
            "2" -> BacenControlType.PRIVATE_DOMESTIC
            "3" -> BacenControlType.PRIVATE_FOREIGN_CONTROL
            else -> null
        }
    }

    /**
     * Map institution bank type code to enum.
     */
    private fun mapInstitutionBankType(code: String?): BacenInstitutionBankType? {
        return when (code?.uppercase()) {
            "B1" -> BacenInstitutionBankType.B1
            "B2" -> BacenInstitutionBankType.B2
            "B3S" -> BacenInstitutionBankType.B3S
            "B3C" -> BacenInstitutionBankType.B3C
            "B4" -> BacenInstitutionBankType.B4
            "N1" -> BacenInstitutionBankType.N1
            "N2" -> BacenInstitutionBankType.N2
            "N4" -> BacenInstitutionBankType.N4
            else -> null
        }
    }

    /**
     * Map institution type based on tipo_consolidacao and group codes.
     * Simplified version - full logic would need codConglomeradoPrudencial and codConglomeradoFinanceiro.
     */
    private fun mapInstitutionType(
        tipoConsolidacao: String?,
        bacenCodInst: String,
        codConglomeradoPrudencial: String?,
        codConglomeradoFinanceiro: String?,
    ): BacenInstitutionType? {
        // Priority: FINANCIAL_GROUP > PRUDENTIAL_GROUP > INDIVIDUAL_INSTITUTION
        return when {
            codConglomeradoFinanceiro != null && bacenCodInst == codConglomeradoFinanceiro -> {
                BacenInstitutionType.FINANCIAL_GROUP
            }
            codConglomeradoPrudencial != null && bacenCodInst == codConglomeradoPrudencial -> {
                BacenInstitutionType.PRUDENTIAL_GROUP
            }
            tipoConsolidacao == "I" -> {
                BacenInstitutionType.INDIVIDUAL_INSTITUTION
            }
            codConglomeradoPrudencial == null && codConglomeradoFinanceiro == null -> {
                BacenInstitutionType.INDIVIDUAL_INSTITUTION
            }
            else -> null
        }
    }
}
