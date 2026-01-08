package io.github.salomax.neotool.financialdata.domain

import io.github.salomax.neotool.financialdata.model.BacenInstitutionEntity
import java.time.Instant

data class BacenInstitution(
    val bacenCodInst: String,
    val anoMesRef: String,
    val sourceHash: String,
    val nomeInstituicao: String? = null,
    val situacao: Char? = null,
    val segmentoTb: String? = null,
    val atividade: String? = null,
    val tipoControle: String? = null,
    val uf: String? = null,
    val municipio: String? = null,
    val codConglomeradoFinanceiro: String? = null,
    val codConglomeradoPrudencial: String? = null,
    val cnpjInstituicaoLider: String? = null,
    val tipoConsolidadoBancario: String? = null,
    val tipoConsolidacao: Char? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): BacenInstitutionEntity {
        return BacenInstitutionEntity(
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
