package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.transaction.Merchant
import io.github.salomax.neotool.banking.domain.transaction.MerchantRepository
import io.github.salomax.neotool.banking.domain.transaction.TopMerchantRow
import io.github.salomax.neotool.banking.model.transaction.MerchantLogoCacheEntity
import io.github.salomax.neotool.banking.model.transaction.MerchantMapper
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultMerchantRepository(
    private val merchantRepo: MerchantEntityRepository,
    private val logoCacheRepo: MerchantLogoCacheEntityRepository,
) : MerchantRepository {

    override fun findAllForAccount(accountId: UUID): List<Merchant> =
        merchantRepo.findAllForAccount(accountId).map(MerchantMapper::toDomain)

    override fun findUserMerchantsForAccount(accountId: UUID): List<Merchant> =
        merchantRepo.findUserMerchantsForAccount(accountId).map(MerchantMapper::toDomain)

    override fun findById(id: UUID): Merchant? =
        merchantRepo.findById(id).orElse(null)?.let(MerchantMapper::toDomain)

    override fun save(merchant: Merchant): Merchant {
        val entity = MerchantMapper.toEntity(merchant)
        return MerchantMapper.toDomain(merchantRepo.save(entity))
    }

    override fun findDistinctDomainsWithoutLogoCache(): List<String> =
        merchantRepo.findDistinctDomainsWithoutLogoCache()

    override fun findLogoAssetIdByDomain(domain: String): String? =
        logoCacheRepo.findByDomain(domain)?.assetId

    override fun saveLogoCache(
        domain: String,
        assetId: String,
    ) {
        logoCacheRepo.save(
            MerchantLogoCacheEntity(
                domain = domain,
                assetId = assetId,
                fetchedAt = Instant.now(),
            ),
        )
    }

    override fun findTopMerchants(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int?,
        month: Int?,
        limit: Int,
    ): List<TopMerchantRow> =
        merchantRepo
            .findTopMerchants(accountId, financialAccountId, year, month, limit)
            .map { row ->
                TopMerchantRow(
                    merchantId = UUID.fromString(row[0].toString()),
                    merchantName = row[1].toString(),
                    merchantDomain = row[2]?.toString(),
                    merchantSector = row[3]?.toString(),
                    transactionCount = (row[4] as Number).toLong(),
                    totalAmount = BigDecimal(row[5].toString()),
                    lastUsedAt = LocalDate.parse(row[6].toString()),
                )
            }
}
