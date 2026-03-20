package io.github.salomax.neotool.banking.domain.transaction

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
) {
    /**
     * Returns all merchants visible to [accountId] (system + user-scoped),
     * suitable for display in a picker or search.
     */
    fun listForAccount(accountId: UUID): List<Merchant> =
        merchantRepository.findAllForAccount(accountId)

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
        domain: String?,
        sector: String?,
        accountId: UUID,
    ): Merchant {
        require(name.isNotBlank()) { "Merchant name must not be blank" }
        val normalizedDomain = domain?.trim()?.lowercase()?.ifBlank { null }
        val now = Instant.now()
        val merchant =
            merchantRepository.save(
                Merchant(
                    name = name.trim(),
                    domain = normalizedDomain,
                    sector = sector?.trim()?.ifBlank { null },
                    isSystem = false,
                    accountId = accountId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

        // Auto-create a CONTAINS rule so this merchant participates in rule engine suggestion.
        // Ignore duplicates in case a rule for the same pattern already exists.
        try {
            ruleService.create(
                input = CreateTransactionRuleInput(
                    merchantId = merchant.id,
                    pattern = normalize(merchant.name),
                    patternType = PatternType.CONTAINS,
                    applicableTo = CategoryApplicableTo.ANY,
                    priority = 40,
                ),
                accountId = accountId,
            )
        } catch (_: DuplicateTransactionRuleException) {
            // A rule with this pattern already exists — merchant is already covered.
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
}
