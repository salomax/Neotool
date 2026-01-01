package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.BankReport
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bank_report", schema = "financial_data")
open class BankReportEntity(
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
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID?>(id) {
    fun toDomain(): BankReport {
        return BankReport(
            id = this.id,
            codInst = this.codInst,
            anoMes = this.anoMes,
            nomeRelatorio = this.nomeRelatorio,
            numeroRelatorio = this.numeroRelatorio,
            tipoInstituicao = this.tipoInstituicao,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
