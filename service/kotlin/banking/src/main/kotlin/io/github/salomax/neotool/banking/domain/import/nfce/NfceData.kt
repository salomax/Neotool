package io.github.salomax.neotool.banking.domain.import.nfce

import java.math.BigDecimal
import java.time.Instant

/**
 * Parsed data from an NFC-e (Nota Fiscal do Consumidor Eletrônica).
 */
data class NfceData(
    val chNFe: String,
    val dhEmi: Instant,
    val vNF: BigDecimal,
    val cnpjEmitente: String,
    val nomeEmitente: String,
    val municipio: String,
    val uf: String,
    val items: List<NfceItem>,
    val cpfDestinatario: String? = null,
    val vTotTrib: BigDecimal? = null,
)
