package io.github.salomax.neotool.banking.repo.account

import io.github.salomax.neotool.banking.model.account.FinancialAccountCardDataEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface FinancialAccountCardDataEntityRepository : JpaRepository<FinancialAccountCardDataEntity, UUID>
