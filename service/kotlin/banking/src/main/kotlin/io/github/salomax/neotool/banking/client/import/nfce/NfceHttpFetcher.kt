package io.github.salomax.neotool.banking.client.import.nfce

/**
 * Fetches raw HTTP response for a given URL (used by [NfceClient] to allow mocking in tests).
 */
fun interface NfceHttpFetcher {
    fun fetch(url: String): NfceHttpResponse
}
