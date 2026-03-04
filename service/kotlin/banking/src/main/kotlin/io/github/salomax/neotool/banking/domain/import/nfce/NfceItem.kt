package io.github.salomax.neotool.banking.domain.import.nfce

import java.math.BigDecimal

/**
 * A single item (product/service) from an NFC-e.
 */
data class NfceItem(
    val xProd: String,
    val vProd: BigDecimal,
    val qCom: BigDecimal,
    val uCom: String,
    val ncm: String,
)
