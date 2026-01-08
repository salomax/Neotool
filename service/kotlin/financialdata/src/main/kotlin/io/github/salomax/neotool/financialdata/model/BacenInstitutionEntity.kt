package io.github.salomax.neotool.financialdata.model

import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.financialdata.domain.BacenInstitution
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "bacen_institution", schema = "financial_data")
open class BacenInstitutionEntity(
    @Id
    @Column(name = "bacen_cod_inst", nullable = false)
    open val bacenCodInst: String,
    @Column(name = "ano_mes_ref", nullable = false, length = 6)
    open var anoMesRef: String,
    @Column(name = "source_hash", nullable = false, length = 64)
    open var sourceHash: String,
    @Column(name = "nome_instituicao")
    open var nomeInstituicao: String? = null,
    @Column(name = "situacao", length = 1)
    open var situacao: Char? = null,
    @Column(name = "segmento_tb")
    open var segmentoTb: String? = null,
    @Column(name = "atividade")
    open var atividade: String? = null,
    @Column(name = "tipo_controle")
    open var tipoControle: String? = null,
    @Column(name = "uf", length = 2)
    open var uf: String? = null,
    @Column(name = "municipio")
    open var municipio: String? = null,
    @Column(name = "cod_conglomerado_financeiro")
    open var codConglomeradoFinanceiro: String? = null,
    @Column(name = "cod_conglomerado_prudencial")
    open var codConglomeradoPrudencial: String? = null,
    @Column(name = "cnpj_instituicao_lider")
    open var cnpjInstituicaoLider: String? = null,
    @Column(name = "tipo_consolidado_bancario")
    open var tipoConsolidadoBancario: String? = null,
    @Column(name = "tipo_consolidacao", length = 1)
    open var tipoConsolidacao: Char? = null,
    @ManyToOne
    @JoinTable(
        name = "financial_institution_bacen_institution",
        schema = "financial_data",
        joinColumns = [JoinColumn(name = "bacen_cod_inst", referencedColumnName = "bacen_cod_inst")],
        inverseJoinColumns = [JoinColumn(name = "financial_institution_id", columnDefinition = "uuid")],
    )
    open var financialInstitution: FinancialInstitutionEntity? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<String>(bacenCodInst) {
    fun toDomain(): BacenInstitution {
        return BacenInstitution(
            bacenCodInst = this.bacenCodInst,
            anoMesRef = this.anoMesRef,
            sourceHash = this.sourceHash,
            nomeInstituicao = this.nomeInstituicao,
            situacao = this.situacao,
            segmentoTb = this.segmentoTb,
            atividade = this.atividade,
            tipoControle = this.tipoControle,
            uf = this.uf,
            municipio = this.municipio,
            codConglomeradoFinanceiro = this.codConglomeradoFinanceiro,
            codConglomeradoPrudencial = this.codConglomeradoPrudencial,
            cnpjInstituicaoLider = this.cnpjInstituicaoLider,
            tipoConsolidadoBancario = this.tipoConsolidadoBancario,
            tipoConsolidacao = this.tipoConsolidacao,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
