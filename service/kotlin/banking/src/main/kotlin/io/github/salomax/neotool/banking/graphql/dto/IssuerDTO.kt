package io.github.salomax.neotool.banking.graphql.dto

import java.util.UUID

/**
 * GraphQL response DTO for [Issuer].
 *
 * [logoAsset] is a federation stub — when [logoKey] is non-null it holds
 * `{ __typename: "Asset", id: logoKey }` so Apollo Router resolves the full
 * Asset from the assets subgraph. Null until logo hydration runs.
 */
data class IssuerDTO(
    val id: UUID,
    val name: String,
    val domain: String?,
    val compeCode: String?,
    val ispb: String?,
    val countryCode: String,
    val logoKey: String?,
    /** Federation reference resolved by [BankingWiringFactory.registerCustomTypeResolvers]. */
    val logoAsset: Map<String, String>?,
)
