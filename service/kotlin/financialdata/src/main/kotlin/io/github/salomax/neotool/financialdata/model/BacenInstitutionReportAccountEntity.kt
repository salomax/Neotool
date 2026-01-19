package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bacen_inst_report_account", schema = "financial_data")
open class BacenInstitutionReportAccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "grupo")
    open var grupo: String? = null,
    @Column(name = "conta")
    open var conta: String? = null,
    @Column(name = "nome_coluna")
    open var nomeColuna: String? = null,
    @Column(name = "descricao_coluna")
    open var descricaoColuna: String? = null,
    @Column(name = "canonical_account_id", columnDefinition = "uuid")
    open var canonicalAccountId: UUID? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
