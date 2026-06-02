package io.github.salomax.neotool.banking.service.import.nfce

import io.github.salomax.neotool.banking.client.import.nfce.NfceClient
import io.github.salomax.neotool.banking.domain.import.nfce.NfceData
import jakarta.inject.Singleton

private val CHNFE_PATTERN = Regex("""\d{44}""")

/**
 * Routes NFC-e fetch requests to the appropriate state-specific [NfceFetcher]
 * based on the UF code in the first 2 digits of the chNFe (access key).
 * Falls back to [DefaultNfceClient] (XML parser) when no specific fetcher is registered.
 */
@Singleton
open class NfceFetcherRouter(
    fetchers: List<NfceFetcher>,
    private val defaultClient: DefaultNfceClient,
) : NfceClient {
    private val fetcherByUfCode: Map<String, NfceFetcher> =
        fetchers.flatMap { fetcher ->
            fetcher.supportedUfCodes().map { uf -> uf to fetcher }
        }.toMap()

    override fun fetch(qrCodeUrl: String): NfceData {
        val chNFe = CHNFE_PATTERN.find(qrCodeUrl)?.value
        val ufCode = chNFe?.take(2)
        val fetcher = ufCode?.let { fetcherByUfCode[it] }
        return fetcher?.fetch(qrCodeUrl) ?: defaultClient.fetch(qrCodeUrl)
    }
}
