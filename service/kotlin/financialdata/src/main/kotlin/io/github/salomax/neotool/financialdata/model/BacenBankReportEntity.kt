package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.BacenBankReport
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bacen_bank_report", schema = "financial_data")
open class BacenBankReportEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "cod_inst", nullable = false)
    open var codInst: String,
    @Column(name = "ano_mes", nullable = false, length = 6)
    open var anoMes: String,
    @Column(name = "nome_relatorio", nullable = false)
    open var nomeRelatorio: String,
    @Column(name = "numero_relatorio", nullable = false)
    open var numeroRelatorio: String,
    @Column(name = "tipo_instituicao", nullable = false)
    open var tipoInstituicao: Int,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id) {
    fun toDomain(): BacenBankReport {
        return BacenBankReport(
            id = this.id,
            codInst = this.codInst,
            anoMes = this.anoMes,
            nomeRelatorio = this.nomeRelatorio,
            numeroRelatorio = this.numeroRelatorio,
            tipoInstituicao = this.tipoInstituicao,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}

