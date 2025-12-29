package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.BankReportDataEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface BankReportDataRepository : JpaRepository<BankReportDataEntity, UUID> {
    fun findByBankReportId(bankReportId: UUID): List<BankReportDataEntity>
}
