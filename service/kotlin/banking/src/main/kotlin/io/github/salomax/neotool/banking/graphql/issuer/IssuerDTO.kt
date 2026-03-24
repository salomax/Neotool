package io.github.salomax.neotool.banking.graphql.issuer

import io.github.salomax.neotool.banking.domain.issuer.IssuerCategory
import java.util.UUID

/**
 * GraphQL response DTO for [Issuer].
 *
 * [logoAsset] is a federation stub — when [logoKey] is non-null it holds
 * `{ __typename: "Asset", id: logoKey }` so Apollo Router resolves the full
 * Asset from the assets subgraph. Null until an external logo job hydrates it.
 */
data class IssuerDTO(
    val id: UUID,
    val name: String,
    val domain: String?,
    val compeCode: String?,
    val ispb: String?,
    val countryCode: String,
    val logoKey: String?,
    val category: IssuerCategory,
    /** Federation reference resolved by [BankingWiringFactory.registerCustomTypeResolvers]. */
    val logoAsset: Map<String, String>?,
)
