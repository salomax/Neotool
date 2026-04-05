package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import java.util.UUID

interface PortfolioRecalculationJobRepository {
    fun save(job: PortfolioRecalculationJob): PortfolioRecalculationJob

    fun update(job: PortfolioRecalculationJob): PortfolioRecalculationJob

    fun findById(id: UUID): PortfolioRecalculationJob?

    fun findPendingByAccount(accountId: UUID): PortfolioRecalculationJob?

    fun findActiveByAccount(accountId: UUID): PortfolioRecalculationJob?

    fun findLatestByAccount(accountId: UUID): PortfolioRecalculationJob?
}
