package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.PurchaseItem
import java.util.UUID

interface PurchaseItemRepository {
    fun saveAll(items: List<PurchaseItem>): List<PurchaseItem>

    fun findByTransactionId(
        transactionId: UUID,
        accountId: UUID,
    ): List<PurchaseItem>

    fun findTop3UnmappedByRawDescription(
        accountId: UUID,
        rawDescription: String,
    ): List<PurchaseItem>
}
