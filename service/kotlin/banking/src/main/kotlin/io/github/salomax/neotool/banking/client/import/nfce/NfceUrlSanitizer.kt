package io.github.salomax.neotool.banking.client.import.nfce

/**
 * Raw NFC-e QR code URLs may contain pipe separators in the query string.
 * Java URI builders reject this form, so we percent-encode only the pieces
 * that are known to appear in valid SEFAZ links.
 */
fun sanitizeNfceUrl(url: String): String = url.trim().replace("|", "%7C")
