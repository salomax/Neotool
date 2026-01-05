package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.BacenBankReportData
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bacen_bank_report_data", schema = "financial_data")
open class BacenBankReportDataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "bank_report_id", nullable = false, columnDefinition = "uuid")
    open var bankReportId: UUID,
    @Column(name = "grupo")
    open var grupo: String? = null,
    @Column(name = "conta")
    open var conta: String? = null,
    @Column(name = "nome_coluna")
    open var nomeColuna: String? = null,
    @Column(name = "descricao_coluna")
    open var descricaoColuna: String? = null,
    @Column(name = "saldo", columnDefinition = "NUMERIC")
    open var saldo: BigDecimal? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id) {
    fun toDomain(): BacenBankReportData {
        return BacenBankReportData(
            id = this.id,
            bankReportId = this.bankReportId,
            grupo = this.grupo,
            conta = this.conta,
            nomeColuna = this.nomeColuna,
            descricaoColuna = this.descricaoColuna,
            saldo = this.saldo,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}

