package io.github.salomax.neotool.banking.service.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.NfceData

/**
 * Strategy interface for fetching and parsing NFC-e data from state-specific SEFAZ portals.
 * Each implementation handles a set of states (UFs) that share the same portal format.
 */
interface NfceFetcher {
    /** UF codes this fetcher supports (e.g., "35" for SP). */
    fun supportedUfCodes(): Set<String>

    /** Fetch and parse full NFC-e data from the QR code URL. */
    fun fetch(qrCodeUrl: String): NfceData
}
