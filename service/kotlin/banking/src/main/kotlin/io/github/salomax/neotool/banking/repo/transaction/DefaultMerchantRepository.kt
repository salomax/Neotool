package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.Merchant
import io.github.salomax.neotool.banking.domain.transaction.MerchantLogoCacheEntry
import io.github.salomax.neotool.banking.domain.transaction.MerchantSearchPage
import io.github.salomax.neotool.banking.domain.transaction.TopMerchantRow
import io.github.salomax.neotool.banking.model.dto.transaction.MerchantCursorCodec
import io.github.salomax.neotool.banking.model.transaction.MerchantMapper
import io.github.salomax.neotool.banking.model.transaction.MerchantLogoCacheEntity
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

    override fun searchForAccount(
        accountId: UUID,
        search: String?,
        after: String?,
        first: Int,
    ): MerchantSearchPage {
        val pageSize = first.coerceIn(1, 50)
        val normalizedSearch = search?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        val searchLikePattern = normalizedSearch?.let(::toContainsIlikePattern)
        val cursor = after?.let(MerchantCursorCodec::decode)

        val pageSlice =
            merchantRepo.searchPageForAccount(
                accountId = accountId,
                searchLikePattern = searchLikePattern,
                afterName = cursor?.name,
                afterId = cursor?.id,
                limit = pageSize + 1,
            ).map(MerchantMapper::toDomain)
        val hasNextPage = pageSlice.size > pageSize
        val pageItems = if (hasNextPage) pageSlice.dropLast(1) else pageSlice
        val startCursor =
            pageItems.firstOrNull()?.let {
                MerchantCursorCodec.encode(it.name, requireNotNull(it.id))
            }
        val endCursor =
            pageItems.lastOrNull()?.let {
                MerchantCursorCodec.encode(it.name, requireNotNull(it.id))
            }

        return MerchantSearchPage(
            items = pageItems,
            totalCount = merchantRepo.countForAccountBySearchLike(accountId, searchLikePattern).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            hasNextPage = hasNextPage,
            hasPreviousPage = false,
            startCursor = startCursor,
            endCursor = endCursor,
        )
    }

    override fun findUserMerchantsForAccount(accountId: UUID): List<Merchant> =
        merchantRepo.findUserMerchantsForAccount(accountId).map(MerchantMapper::toDomain)

    override fun findById(id: UUID): Merchant? =
        merchantRepo.findById(id).orElse(null)?.let(MerchantMapper::toDomain)

    override fun findByIds(ids: Collection<UUID>): List<Merchant> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            merchantRepo.findByIdIn(ids).map(MerchantMapper::toDomain)
        }

    override fun save(merchant: Merchant): Merchant {
        val entity = MerchantMapper.toEntity(merchant)
        return MerchantMapper.toDomain(merchantRepo.save(entity))
    }

    override fun findLogoAssetIdByDomain(domain: String): UUID? =
        logoCacheRepo.findByDomain(domain)?.assetId

    override fun findLogoCacheByDomain(domain: String): MerchantLogoCacheEntry? =
        logoCacheRepo.findByDomainIgnoreCase(domain)?.let { cache ->
            MerchantLogoCacheEntry(
                domain = cache.domain,
                assetId = cache.assetId,
                fetchedAt = cache.fetchedAt,
            )
        }

    override fun findLogoAssetIdsByDomains(domains: Collection<String>): Map<String, UUID> =
        if (domains.isEmpty()) {
            emptyMap()
        } else {
            logoCacheRepo.findByDomainIn(domains).associate { it.domain to it.assetId }
        }

    override fun upsertLogoCache(
        domain: String,
        assetId: UUID,
        fetchedAt: Instant,
    ) {
        val existing = logoCacheRepo.findByDomainIgnoreCase(domain)
        if (existing == null) {
            logoCacheRepo.save(MerchantLogoCacheEntity(domain = domain, assetId = assetId, fetchedAt = fetchedAt))
            return
        }
        existing.assetId = assetId
        existing.fetchedAt = fetchedAt
        logoCacheRepo.update(existing)
    }

    override fun findDomainsMissingLogo(limit: Int): List<String> =
        merchantRepo.findDomainsMissingLogo(limit.coerceIn(1, 500))

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

    /**
     * Case-insensitive substring search for the GraphQL merchant picker (`%term%`), not whole-word rule matching.
     * Escapes `%`, `_`, and `\` for PostgreSQL `ILIKE ... ESCAPE E'\\'`.
     */
    private fun toContainsIlikePattern(term: String): String {
        val escaped =
            term
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
        return "%$escaped%"
    }
}
