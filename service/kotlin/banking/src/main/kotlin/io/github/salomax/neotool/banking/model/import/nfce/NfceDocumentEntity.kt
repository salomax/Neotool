package io.github.salomax.neotool.banking.model.import.nfce

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "purchase_documents", schema = "banking")
open class NfceDocumentEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(name = "transaction_id", nullable = false)
    open var transactionId: UUID,
    @Column(name = "account_id", nullable = false)
    open var accountId: UUID,
    @Column(name = "access_key", nullable = false)
    open var accessKey: String,
    @Column(name = "issuer_cnpj", nullable = false)
    open var issuerCnpj: String,
    @Column(name = "issuer_name", nullable = false)
    open var issuerName: String,
    @Column(name = "issuer_trade_name")
    open var issuerTradeName: String? = null,
    @Column(name = "issuer_state_registration")
    open var issuerStateRegistration: String? = null,
    @Column(name = "issuer_address")
    open var issuerAddress: String? = null,
    @Column(name = "issuer_postal_code")
    open var issuerPostalCode: String? = null,
    @Column(name = "emission_date", nullable = false)
    open var emissionDate: Instant,
    @Column(name = "total_value", nullable = false, precision = 18, scale = 4)
    open var totalValue: BigDecimal,
    @Column(name = "total_tax", precision = 18, scale = 4)
    open var totalTax: BigDecimal? = null,
    @Column(name = "buyer_cpf")
    open var buyerCpf: String? = null,
    @Column(name = "municipality_code")
    open var municipalityCode: String? = null,
    @Column(name = "municipality")
    open var municipality: String? = null,
    @Column(name = "state", nullable = false)
    open var state: String,
    @Column(name = "country_code", nullable = false, length = 2)
    open var countryCode: String = "BR",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_data", columnDefinition = "jsonb")
    open var sourceData: Map<String, String?>? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
