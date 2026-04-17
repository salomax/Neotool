package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.NfceDocument
import java.util.UUID

interface NfceDocumentRepository {
    fun findByAccessKeyAndAccountId(
        accessKey: String,
        accountId: UUID,
    ): NfceDocument?

    fun findByTransactionId(
        transactionId: UUID,
        accountId: UUID,
    ): NfceDocument?

    fun findLinkedTransactionIds(
        accountId: UUID,
        transactionIds: List<UUID>,
    ): Set<UUID>

    fun deleteByAccessKeyAndAccountIdForDeletedTransactions(
        accessKey: String,
        accountId: UUID,
    ): Int

    fun save(document: NfceDocument): NfceDocument
}
