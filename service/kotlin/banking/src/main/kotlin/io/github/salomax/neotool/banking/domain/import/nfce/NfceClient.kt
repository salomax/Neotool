package io.github.salomax.neotool.banking.domain.import.nfce

/**
 * Fetches NFC-e data from a QR code URL (typically a government or issuer endpoint).
 */
fun interface NfceClient {
    fun fetch(qrCodeUrl: String): NfceData
}
