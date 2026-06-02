package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.AnbimaCategory
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "investment_assets", schema = "banking")
open class AssetEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(length = 20)
    open var ticker: String? = null,
    @Column(name = "isin_code", length = 12)
    open var isinCode: String? = null,
    @Column(nullable = false)
    open var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 48)
    open var assetType: AssetType,
    @Column(length = 10)
    open var exchange: String? = null,
    @Column(name = "currency_code", nullable = false, length = 3)
    open var currencyCode: String = "BRL",
    @Column(name = "issuer_cnpj", length = 14)
    open var issuerCnpj: String? = null,
    @Column(name = "issuer_name")
    open var issuerName: String? = null,
    @Column(name = "fund_cnpj", length = 14)
    open var fundCnpj: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "anbima_category", length = 20)
    open var anbimaCategory: AnbimaCategory? = null,
    @Column(name = "anbima_class")
    open var anbimaClass: String? = null,
    @Column(name = "anbima_subclass")
    open var anbimaSubclass: String? = null,
    @Column(name = "price_factor", nullable = false, precision = 10, scale = 6)
    open var priceFactor: BigDecimal = BigDecimal.ONE,
    @Column(name = "provider_id")
    open var providerId: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
