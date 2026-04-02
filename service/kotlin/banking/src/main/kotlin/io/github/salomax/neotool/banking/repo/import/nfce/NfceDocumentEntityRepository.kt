package io.github.salomax.neotool.banking.repo.import.nfce

import io.github.salomax.neotool.banking.model.import.nfce.NfceDocumentEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface NfceDocumentEntityRepository : JpaRepository<NfceDocumentEntity, UUID> {
    fun findByAccessKeyAndAccountId(
        accessKey: String,
        accountId: UUID,
    ): NfceDocumentEntity?

    fun findByTransactionIdAndAccountId(
        transactionId: UUID,
        accountId: UUID,
    ): NfceDocumentEntity?

    @Query(
        """
        select d.transactionId
        from NfceDocumentEntity d
        where d.accountId = :accountId
          and d.transactionId in (:transactionIds)
        """,
    )
    fun findTransactionIdsByAccountIdAndTransactionIdIn(
        accountId: UUID,
        transactionIds: List<UUID>,
    ): List<UUID>
}
