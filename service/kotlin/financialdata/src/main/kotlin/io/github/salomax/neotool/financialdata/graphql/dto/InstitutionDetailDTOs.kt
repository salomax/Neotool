package io.github.salomax.neotool.financialdata.graphql.dto

import io.github.salomax.neotool.financialdata.domain.BacenControlType
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionBankType
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class BacenFinanceInstitutionDTO(
    val id: String,
    val name: String,
    val legalName: String?,
    val tradeName: String?,
    val assetId: String?,
    val profit: Double?,
    val equity: Double?,
    val yoyEquityGrowth: Double?,
    val basileiaIndex: Double?,
    val bacenInstitutionType: String?,
    val institutionType: BacenInstitutionType?,
    val tipoConsolidadoBancario: String?,
    val quarter: String?,
    val website: String?,
    val socialNetworks: List<String>?,
    val mainActivity: String?,
    val address: String?,
    val founded: String?,
    val controlType: BacenControlType?,
    val institutionBankType: BacenInstitutionBankType?,
    val mobilizacaoIndex: Double?,
    val totalAssets: Double?,
    val funding: Double?,
    val loanPortfolio: Double?,
    val rwaReferenceEquity: Double?,
)

@Introspected
@Serdeable
data class ReportsAvailabilityDTO(
    val quarters: List<QuarterReportsDTO>,
)

@Introspected
@Serdeable
data class QuarterReportsDTO(
    val quarter: String,
    val reports: List<ReportDescriptorDTO>,
)

@Introspected
@Serdeable
data class ReportDescriptorDTO(
    val reportId: String,
    val name: String?,
    val group: String?,
    val accountNumber: String?,
    val columnName: String?,
)

@Introspected
@Serdeable
data class ReportDetailsDTO(
    val reportId: String,
    val name: String?,
    val group: String?,
    val accountNumber: String?,
    val columnName: String?,
    val valuesByQuarter: List<QuarterValueDTO>,
)

@Introspected
@Serdeable
data class QuarterValueDTO(
    val quarter: String,
    val value: Double?,
)
