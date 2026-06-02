package io.github.salomax.neotool.banking.repo.account

import io.github.salomax.neotool.banking.model.account.FinancialAccountBankDataEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface FinancialAccountBankDataEntityRepository : JpaRepository<FinancialAccountBankDataEntity, UUID>
