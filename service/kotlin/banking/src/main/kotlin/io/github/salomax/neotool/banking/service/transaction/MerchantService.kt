package io.github.salomax.neotool.banking.service.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.DuplicateTransactionRuleException
import io.github.salomax.neotool.banking.domain.transaction.Merchant
import io.github.salomax.neotool.banking.domain.transaction.MerchantSearchPage
import io.github.salomax.neotool.banking.domain.transaction.PatternType
import io.github.salomax.neotool.banking.domain.transaction.TopMerchantRow
import io.github.salomax.neotool.banking.model.domain.transaction.MerchantWebsiteNormalizer
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionRuleInput
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRuleRepository
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

/**
 * Domain service for [Merchant] operations.
 *
 * Merchant suggestion is handled by [TransactionRuleEngine] — each user-created merchant
 * automatically gets a CONTAINS rule so it participates in the unified rule engine pass.
 */
@Singleton
open class MerchantService(
    private val merchantRepository: MerchantRepository,
    private val ruleService: TransactionRuleService,
    private val ruleRepository: TransactionRuleRepository,
    private val merchantLogoHydrationService: MerchantLogoHydrationService,
) {
    /**
     * Returns all merchants visible to [accountId] (system + user-scoped),
     * suitable for display in a picker or search.
     */
    fun listForAccount(accountId: UUID): List<Merchant> = merchantRepository.findAllForAccount(accountId)

    fun search(
        accountId: UUID,
        search: String?,
        after: String?,
        first: Int?,
    ): MerchantSearchPage {
        val pageSize = (first ?: 20).coerceIn(1, 50)
        val normalizedSearch = search?.trim()?.takeIf { it.isNotEmpty() }
        return merchantRepository.searchForAccount(accountId, normalizedSearch, after, pageSize)
    }

    /**
     * Finds a merchant by ID. Returns null if not found.
     * Caller is responsible for validating that the account can access it.
     */
    fun findById(id: UUID): Merchant? = merchantRepository.findById(id)

    /**
     * Creates a user-scoped merchant for [accountId] and automatically registers a CONTAINS rule
     * so the merchant is suggested by the rule engine for matching transaction descriptions.
     */
    fun create(
        name: String,
        website: String?,
        sector: String?,
        accountId: UUID,
    ): Merchant {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Merchant name must not be blank" }
        require(trimmedName.length <= 100) { "Merchant name must be at most 100 characters" }

        val rawWebsite = website?.trim()
        val normalizedDomain = MerchantWebsiteNormalizer.normalizeDomain(rawWebsite)
        if (!rawWebsite.isNullOrBlank() && normalizedDomain == null) {
            throw IllegalArgumentException("website must be a valid domain")
        }

        val now = Instant.now()
        val normalizedName = normalize(trimmedName)
        val merchant =
            merchantRepository.save(
                Merchant(
                    name = trimmedName,
                    domain = normalizedDomain,
                    sector = sector?.trim()?.ifBlank { null },
                    isSystem = false,
                    accountId = accountId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

        normalizedDomain?.let { merchantLogoHydrationService.hydrateForDomainAsync(it) }

        // Auto-create a CONTAINS rule so this merchant participates in rule engine suggestion.
        // Ignore duplicates in case a rule for the same pattern already exists.
        try {
            ruleService.create(
                input =
                    CreateTransactionRuleInput(
                        merchantId = merchant.id,
                        pattern = normalizedName,
                        patternType = PatternType.CONTAINS,
                        applicableTo = CategoryApplicableTo.ANY,
                        priority = 40,
                    ),
                accountId = accountId,
            )
        } catch (_: DuplicateTransactionRuleException) {
            enrichExistingRuleWithMerchant(accountId, normalizedName, requireNotNull(merchant.id))
        }

        return merchant
    }

    /**
     * Returns the top merchants by transaction count for [accountId].
     */
    fun topMerchants(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int?,
        month: Int?,
        limit: Int = 10,
    ): List<TopMerchantRow> =
        merchantRepository.findTopMerchants(
            accountId = accountId,
            financialAccountId = financialAccountId,
            year = year,
            month = month,
            limit = limit.coerceIn(1, 50),
        )

    internal fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("[^\\p{ASCII}]"), "")
            .replace(Regex("[^a-zA-Z0-9\\s*]"), " ")
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun enrichExistingRuleWithMerchant(
        accountId: UUID,
        pattern: String,
        merchantId: UUID,
    ) {
        val existing =
            ruleRepository.findUserRulesForAccount(accountId)
                .firstOrNull {
                    it.pattern == pattern &&
                        it.patternType == PatternType.CONTAINS &&
                        it.merchantId == null
                } ?: return

        ruleRepository.save(
            existing.copy(
                merchantId = merchantId,
                updatedAt = Instant.now(),
            ),
        )
    }
}
