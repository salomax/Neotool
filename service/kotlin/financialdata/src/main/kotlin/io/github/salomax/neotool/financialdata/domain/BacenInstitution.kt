package io.github.salomax.neotool.financialdata.domain

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
)
