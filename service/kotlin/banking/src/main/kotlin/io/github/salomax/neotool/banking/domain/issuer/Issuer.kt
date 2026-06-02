package io.github.salomax.neotool.banking.domain.issuer

import java.util.UUID

enum class IssuerCategory {
    BANK,
    BROKERAGE,
    CRYPTO_EXCHANGE,
    PAYMENT,
    BENEFIT,
}

/**
 * Domain model for a financial institution (banking.issuers).
 * Used as the institution picker in account creation.
 * logo_key may be null until the issuer logo worker hydrates it.
 */
data class Issuer(
    val id: UUID? = null,
    val name: String,
    val domain: String?,
    val compeCode: String?,
    val ispb: String?,
    val logoKey: String?,
    val countryCode: String = "BR",
    val isActive: Boolean = true,
    val category: IssuerCategory = IssuerCategory.BANK,
)
