package io.github.salomax.neotool.banking.domain.issuer

import java.util.UUID

/**
 * Domain model for a financial institution (banking.issuers).
 * Used as the institution picker in account creation.
 * logo_key is null until the IssuerLogoHydrationJob runs.
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
)
