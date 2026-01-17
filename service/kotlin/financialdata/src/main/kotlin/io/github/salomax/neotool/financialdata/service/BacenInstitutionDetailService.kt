package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.financialdata.domain.BacenControlType
import io.github.salomax.neotool.financialdata.domain.BacenFinanceInstitution
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionBankType
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.github.salomax.neotool.financialdata.domain.CanonicalAccountNames
import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.domain.QuarterReports
import io.github.salomax.neotool.financialdata.domain.QuarterValue
import io.github.salomax.neotool.financialdata.domain.ReportAccount
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
    private val quarterService: QuarterService,
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
    fun getBacenFinanceInstitution(
        id: String,
        anoMes: String? = null,
    ): BacenFinanceInstitution? {
        logger.debug { "Fetching BacenFinanceInstitution for id=$id, anoMes=$anoMes" }

        val targetAnoMes = anoMes ?: quarterService.getLatestQuarter()
        if (targetAnoMes == null) {
            logger.debug { "Latest quarter not available for id=$id" }
            return null
        }

        // Get financial institution data
        val financialData = institutionDetailRepository.getFinancialInstitutionData(id, targetAnoMes)
        if (financialData == null) {
            logger.debug { "Financial institution not found for id=$id, anoMes=$anoMes" }
            return null
        }

        // Get BACEN institution report data
        val reportData = institutionDetailRepository.getBacenInstitutionReportData(id, targetAnoMes)
        if (reportData.isEmpty()) {
            logger.debug { "No report data found for id=$id, anoMes=$anoMes" }
        }

        // Extract financial metrics from report data
        val financialMetrics = extractFinancialMetrics(reportData, targetAnoMes)

        // Map control type
        val controlType = mapControlType(financialData.tipoControle)

        // Map institution bank type
        val institutionBankType = mapInstitutionBankType(financialData.tipoConsolidadoBancario)

        // Map institution type
        val institutionType =
            mapInstitutionType(
                tipoConsolidacao = financialData.tipoConsolidacao,
                bacenCodInst = id,
                // Not available in detail result
                codConglomeradoPrudencial = null,
                // Not available in detail result
                codConglomeradoFinanceiro = null,
            )

        // Get latest quarter from report data
        val latestQuarter = reportData.firstOrNull()?.anoMesRef

        return BacenFinanceInstitution(
            id = id,
            name = financialData.nomeInstituicao ?: "Unknown",
            legalName = financialData.legalName,
            tradeName = financialData.tradeName,
            assetId = financialData.assetId,
            profit = financialMetrics.profit,
            equity = financialMetrics.equity,
            yoyEquityGrowth = financialMetrics.yoyEquityGrowth,
            basileiaIndex = financialMetrics.basileiaIndex,
            institutionType = institutionType,
            tipoConsolidadoBancario = institutionBankType,
            quarter = latestQuarter,
            website = financialData.website,
            socialNetworks = financialData.socialNetworks,
            mainActivity = financialData.mainActivity,
            address = financialData.address,
            founded = financialData.founded,
            controlType = controlType,
            institutionBankType = institutionBankType,
            mobilizacaoIndex = financialMetrics.mobilizacaoIndex,
            totalAssets = financialMetrics.totalAssets,
            funding = financialMetrics.funding,
            loanPortfolio = financialMetrics.loanPortfolio,
            rwaReferenceEquity = financialMetrics.rwaReferenceEquity,
        )
    }

    /**
     * Extract financial metrics from report data.
     * Maps canonical account names to specific financial metrics.
     */
    private fun extractFinancialMetrics(
        reportData: List<io.github.salomax.neotool.financialdata.repo.result.BacenInstitutionReportDataResult>,
        anoMes: String?,
    ): FinancialMetrics {
        // Group by canonical account name for easier lookup
        // Prefer canonical account name, fallback to account number for backward compatibility
        val accountMap = reportData.associateBy { it.canonicalAccountName ?: it.account }

        // Expected canonical account names for financial metrics
        val expectedAccounts =
            setOf(
                CanonicalAccountNames.LUCRO_LIQUIDO,
                CanonicalAccountNames.PATRIMONIO_LIQUIDO,
                CanonicalAccountNames.INDICE_DE_BASILEIA,
                CanonicalAccountNames.INDICE_DE_IMOBILIZACAO,
            CanonicalAccountNames.ATIVO_TOTAL,
            CanonicalAccountNames.CAPTACOES,
            CanonicalAccountNames.CARTEIRA_DE_CREDITO,
            CanonicalAccountNames.PATRIMONIO_DE_REFERENCIA,
        )
        val missingAccounts = expectedAccounts.filter { it !in accountMap }
        if (missingAccounts.isNotEmpty()) {
            logger.debug { "Missing expected canonical account names in report data: $missingAccounts" }
        }

        val profit = accountMap[CanonicalAccountNames.LUCRO_LIQUIDO]?.balance
        val equity = accountMap[CanonicalAccountNames.PATRIMONIO_LIQUIDO]?.balance
        val basileiaIndex = accountMap[CanonicalAccountNames.INDICE_DE_BASILEIA]?.balance
        val mobilizacaoIndex = accountMap[CanonicalAccountNames.INDICE_DE_IMOBILIZACAO]?.balance
        val totalAssets = accountMap[CanonicalAccountNames.ATIVO_TOTAL]?.balance
        val funding = accountMap[CanonicalAccountNames.CAPTACOES]?.balance
        val loanPortfolio = accountMap[CanonicalAccountNames.CARTEIRA_DE_CREDITO]?.balance
        val rwaReferenceEquity = accountMap[CanonicalAccountNames.PATRIMONIO_DE_REFERENCIA]?.balance

        // Calculate year-over-year equity growth
        // TODO: Implement YoY equity growth calculation
        // This requires comparing current period equity (PATRIMONIO_LIQUIDO) with equity from the same period
        // a year ago. This would need a separate query to fetch historical data or modification of
        // the repository to support year-over-year comparisons.
        val yoyEquityGrowth: java.math.BigDecimal? = null

        return FinancialMetrics(
            profit = profit,
            equity = equity,
            yoyEquityGrowth = yoyEquityGrowth,
            basileiaIndex = basileiaIndex,
            mobilizacaoIndex = mobilizacaoIndex,
            totalAssets = totalAssets,
            funding = funding,
            loanPortfolio = loanPortfolio,
            rwaReferenceEquity = rwaReferenceEquity,
        )
    }

    /**
     * Internal data class for financial metrics extracted from report data.
     */
    private data class FinancialMetrics(
        val profit: java.math.BigDecimal?,
        val equity: java.math.BigDecimal?,
        val yoyEquityGrowth: java.math.BigDecimal?,
        val basileiaIndex: java.math.BigDecimal?,
        val mobilizacaoIndex: java.math.BigDecimal?,
        val totalAssets: java.math.BigDecimal?,
        val funding: java.math.BigDecimal?,
        val loanPortfolio: java.math.BigDecimal?,
        val rwaReferenceEquity: java.math.BigDecimal?,
    )

    /**
     * Get available reports grouped by quarter for an institution.
     * Returns only report catalog information (reportId and name).
     * Detailed account/group/column information is available in reportDetails.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param range Quarter range filter
     * @return ReportsAvailability with quarters and their reports
     */
    fun getReportsSummary(
        id: String,
        range: QuarterRange,
    ): ReportsAvailability {
        val reports = institutionDetailRepository.getReportsByQuarterRange(id, range)

        // Group by quarter
        val quartersMap = reports.groupBy { it.quarter }

        val quarterReports =
            quartersMap.map { (quarter, reports) ->
                // Deduplicate by reportId (defensive - SQL DISTINCT should handle this)
                val uniqueReports = reports.distinctBy { it.reportId }

                QuarterReports(
                    quarter = quarter,
                    reports =
                        uniqueReports.map { report ->
                            ReportDescriptor(
                                reportId = report.reportId,
                                name = report.name,
                                // group, accountId, accountName not set (default to null)
                        // These fields are only available in reportDetails
                    )
                },
            )
        }.sortedByDescending { it.quarter } // Most recent first

        return ReportsAvailability(quarters = quarterReports)
    }

    /**
     * Get detailed report values by quarter for a specific report.
     * Groups values by account (group, accountId, accountName) and returns all accounts.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param reportId Report ID (numero_relatorio)
     * @param range Quarter range filter
     * @return ReportDetails or null if report not found
     */
    fun getReportDetails(
        id: String,
        reportId: String,
        range: QuarterRange,
    ): ReportDetails? {
        logger.debug { "Fetching report details for id=$id, reportId=$reportId, range=$range" }

        val values =
            institutionDetailRepository.getReportDetailsByQuarterRange(id, reportId, range)

        if (values.isEmpty()) {
            logger.debug { "No report details found for id=$id, reportId=$reportId, range=$range" }
            return null
        }

        // Use first value for report metadata (all should have same report metadata)
        val first = values.first()

        // Group values by account (composite key: accountGroup, accountId, accountName)
        val accountsMap =
            values.groupBy { value ->
                Triple(
                    value.accountGroup,
                    value.accountId,
                    value.accountName,
                )
        }

        // Create ReportAccount for each unique account
        val accounts = accountsMap.map { (accountKey, accountValues) ->
            val (group, accountId, accountName) = accountKey

            // Aggregate valuesByQuarter per account, sorted descending (most recent first)
            val quarterValues = accountValues.map { value ->
                QuarterValue(
                    quarter = value.quarter,
                    value = value.value,
                )
            }.sortedByDescending { it.quarter }

            ReportAccount(
                group = group,
                accountId = accountId,
                accountName = accountName,
                valuesByQuarter = quarterValues,
            )
        }.sortedWith(
            // Sort accounts consistently: by accountId then group
            compareBy<ReportAccount> { it.accountId ?: "" }
                .thenBy { it.group ?: "" },
        )

        return ReportDetails(
            reportId = first.reportId,
            name = first.name,
            accounts = accounts,
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
     *
     * TODO: This is a simplified implementation. The full logic requires:
     * - codConglomeradoPrudencial: Prudential conglomerate code from bacen_institution
     * - codConglomeradoFinanceiro: Financial conglomerate code from bacen_institution
     *
     * These fields are currently not available in FinancialInstitutionDataResult and would need to be
     * added to the repository query to enable proper institution type mapping.
     *
     * Priority: FINANCIAL_GROUP > PRUDENTIAL_GROUP > INDIVIDUAL_INSTITUTION
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
            else -> {
                logger.debug {
                    "Could not determine institution type for " +
                        "bacenCodInst=$bacenCodInst, tipoConsolidacao=$tipoConsolidacao"
                }
                null
            }
        }
    }
}
