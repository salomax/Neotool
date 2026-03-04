package io.github.salomax.neotool.banking.domain.import

import java.util.UUID

/**
 * Domain repository for [ImportJob].
 * All queries are scoped by account (tenant) for isolation.
 */
interface ImportJobRepository {
    fun save(job: ImportJob): ImportJob
    fun update(job: ImportJob): ImportJob
    fun findById(id: UUID, accountId: UUID): ImportJob?
    fun findByFileHash(financialAccountId: UUID, fileHash: String): ImportJob?
    fun findByFinancialAccount(financialAccountId: UUID, accountId: UUID): List<ImportJob>
    fun findByAccount(accountId: UUID): List<ImportJob>
}
