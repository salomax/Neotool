package io.github.salomax.neotool.banking.model.transaction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for banking.merchant_logo_cache.
 *
 * Keyed by [domain] (e.g. "netflix.com"). Multiple merchants sharing the same
 * domain resolve to a single cached [assetId] from the assets service.
 */
@Entity
@Table(name = "merchant_logo_cache", schema = "banking")
open class MerchantLogoCacheEntity(
    @Id
    @Column(length = 200, nullable = false)
    open val domain: String,
    /** UUID returned by the assets service after upload. */
    @Column(name = "asset_id", nullable = false)
    open var assetId: UUID,
    @Column(name = "fetched_at", nullable = false)
    open var fetchedAt: Instant = Instant.now(),
)
