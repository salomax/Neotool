package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.BacenBankReportDataEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface BacenBankReportDataRepository : JpaRepository<BacenBankReportDataEntity, UUID> {
    fun findByBankReportId(bankReportId: UUID): List<BacenBankReportDataEntity>
}

