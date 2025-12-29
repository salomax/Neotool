package io.github.salomax.neotool.financialdata.model

import com.fasterxml.jackson.databind.JsonNode
import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.ParentInstitution
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "parent_institution", schema = "financial_data")
open class ParentInstitutionEntity(
    @Id
    @Column(name = "parent_cnpj", nullable = false)
    val parentCnpj: String,
    @Column(name = "legal_name")
    open var legalName: String? = null,
    @Column(name = "trade_name")
    open var tradeName: String? = null,
    @Column(name = "email_domain")
    open var emailDomain: String? = null,
    @Column(name = "website")
    open var website: String? = null,
    @Column(name = "logo_storage_key")
    open var logoStorageKey: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "social_network", columnDefinition = "jsonb")
    open var socialNetwork: JsonNode? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "receita_data", columnDefinition = "jsonb")
    open var receitaData: JsonNode? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<String>(parentCnpj) {
    fun toDomain(): ParentInstitution {
        return ParentInstitution(
            parentCnpj = this.parentCnpj,
            legalName = this.legalName,
            tradeName = this.tradeName,
            emailDomain = this.emailDomain,
            website = this.website,
            logoStorageKey = this.logoStorageKey,
            socialNetwork = this.socialNetwork,
            receitaData = this.receitaData,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
