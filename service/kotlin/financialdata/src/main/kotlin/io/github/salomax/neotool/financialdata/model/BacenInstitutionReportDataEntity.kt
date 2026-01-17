package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionReportData
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
@Table(name = "bacen_inst_report_data", schema = "financial_data")
open class BacenInstitutionReportDataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "inst_report_id", nullable = false, columnDefinition = "uuid")
    open var instReportId: UUID,
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
    @Column(name = "saldo_ajustado", columnDefinition = "NUMERIC")
    open var saldoAjustado: BigDecimal? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id) {
    fun toDomain(): BacenInstitutionReportData {
        return BacenInstitutionReportData(
            id = this.id,
            instReportId = this.instReportId,
            grupo = this.grupo,
            conta = this.conta,
            nomeColuna = this.nomeColuna,
            descricaoColuna = this.descricaoColuna,
            saldo = this.saldo,
            saldoAjustado = this.saldoAjustado,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
