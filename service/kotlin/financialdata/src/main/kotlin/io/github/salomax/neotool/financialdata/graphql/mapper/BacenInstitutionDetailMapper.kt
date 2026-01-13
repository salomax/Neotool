package io.github.salomax.neotool.financialdata.graphql.mapper

import io.github.salomax.neotool.financialdata.domain.BacenFinanceInstitution
import io.github.salomax.neotool.financialdata.domain.QuarterReports
import io.github.salomax.neotool.financialdata.domain.QuarterValue
import io.github.salomax.neotool.financialdata.domain.ReportAccount
import io.github.salomax.neotool.financialdata.domain.ReportDescriptor
import io.github.salomax.neotool.financialdata.domain.ReportDetails
import io.github.salomax.neotool.financialdata.domain.ReportsAvailability
import io.github.salomax.neotool.financialdata.graphql.dto.BacenFinanceInstitutionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.QuarterReportsDTO
import io.github.salomax.neotool.financialdata.graphql.dto.QuarterValueDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportAccountDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportDescriptorDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportDetailsDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportsAvailabilityDTO
import jakarta.inject.Singleton

@Singleton
class BacenInstitutionDetailMapper {
    fun toBacenFinanceInstitutionDTO(institution: BacenFinanceInstitution): BacenFinanceInstitutionDTO {
        return BacenFinanceInstitutionDTO(
            id = institution.id,
            name = institution.name,
            legalName = institution.legalName,
            tradeName = institution.tradeName,
            assetId = institution.assetId?.toString(),
            profit = institution.profit?.toDouble(),
            equity = institution.equity?.toDouble(),
            yoyEquityGrowth = institution.yoyEquityGrowth?.toDouble(),
            basileiaIndex = institution.basileiaIndex?.toDouble(),
            institutionType = institution.institutionType,
            tipoConsolidadoBancario = institution.tipoConsolidadoBancario,
            quarter = institution.quarter,
            website = institution.website,
            socialNetworks = institution.socialNetworks,
            mainActivity = institution.mainActivity,
            address = institution.address,
            founded = institution.founded,
            controlType = institution.controlType,
            institutionBankType = institution.institutionBankType,
            mobilizacaoIndex = institution.mobilizacaoIndex?.toDouble(),
            totalAssets = institution.totalAssets?.toDouble(),
            funding = institution.funding?.toDouble(),
            loanPortfolio = institution.loanPortfolio?.toDouble(),
            rwaReferenceEquity = institution.rwaReferenceEquity?.toDouble(),
        )
    }

    fun toReportsAvailabilityDTO(availability: ReportsAvailability): ReportsAvailabilityDTO {
        return ReportsAvailabilityDTO(
            quarters = availability.quarters.map { toQuarterReportsDTO(it) },
        )
    }

    fun toQuarterReportsDTO(quarterReports: QuarterReports): QuarterReportsDTO {
        return QuarterReportsDTO(
            quarter = quarterReports.quarter,
            reports = quarterReports.reports.map { toReportDescriptorDTO(it) },
        )
    }

    fun toReportDescriptorDTO(descriptor: ReportDescriptor): ReportDescriptorDTO {
        return ReportDescriptorDTO(
            reportId = descriptor.reportId,
            name = descriptor.name,
            group = descriptor.group,
            accountNumber = descriptor.accountNumber,
            columnName = descriptor.columnName,
        )
    }

    fun toReportDetailsDTO(details: ReportDetails): ReportDetailsDTO {
        return ReportDetailsDTO(
            reportId = details.reportId,
            name = details.name,
            accounts = details.accounts.map { toReportAccountDTO(it) },
        )
    }

    fun toReportAccountDTO(account: ReportAccount): ReportAccountDTO {
        return ReportAccountDTO(
            group = account.group,
            accountNumber = account.accountNumber,
            columnName = account.columnName,
            valuesByQuarter = account.valuesByQuarter.map { toQuarterValueDTO(it) },
        )
    }

    fun toQuarterValueDTO(value: QuarterValue): QuarterValueDTO {
        return QuarterValueDTO(
            quarter = value.quarter,
            value = value.value?.toDouble(),
        )
    }
}
