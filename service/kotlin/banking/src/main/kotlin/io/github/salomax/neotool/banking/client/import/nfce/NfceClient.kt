package io.github.salomax.neotool.banking.client.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.NfceData

/**
 * Fetches NFC-e data from a QR code URL (typically a government or issuer endpoint).
 */
fun interface NfceClient {
    fun fetch(qrCodeUrl: String): NfceData
}
