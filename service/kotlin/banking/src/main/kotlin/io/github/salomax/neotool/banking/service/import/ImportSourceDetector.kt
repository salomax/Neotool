package io.github.salomax.neotool.banking.service.import

import io.github.salomax.neotool.banking.domain.import.*
import java.text.Normalizer

private const val PEEK_BYTES = 256
private val OFX_HEADER_MARKER = "OFXHEADER:"
private val OFX_TAG_MARKER = "<OFX"
private val NUBANK_CSV_HEADERS_NORMALIZED =
    setOf(
        "date,transaction_type,title,amount,category,charges,identificador",
        "data,valor,identificador,descricao",
    )
private val ITAU_CSV_HEADER_HINTS =
    setOf(
        "data_lancamento",
        "historico",
        "documento",
        "valor",
    )
private val BRADESCO_CSV_HEADER_HINTS =
    setOf(
        "data",
        "historico",
        "docto",
        "credito",
        "debito",
    )
private val SANTANDER_CSV_HEADER_HINTS =
    setOf(
        "data",
        "hora",
        "descricao",
        "valor",
        "saldo",
    )

/**
 * Detects [ImportSource] from file bytes and optional file name.
 * Order of checks matters; matching is case-insensitive.
 */
object ImportSourceDetector {
    /**
     * @return The detected import source
     * @throws UnsupportedImportSourceException if source cannot be determined
     */
    fun detect(
        fileBytes: ByteArray,
        fileName: String?,
    ): ImportSource {
        val decoded = ImportTextDecoder.decode(fileBytes)
        val peek = decoded.text.take(PEEK_BYTES)
        val peekLower = peek.lowercase()

        if (peekLower.contains(OFX_HEADER_MARKER.lowercase()) || peekLower.contains(OFX_TAG_MARKER.lowercase())) {
            return ImportSource.OFX
        }

        val firstLine =
            decoded.text
                .lineSequence()
                .firstOrNull()
                ?.normalizeCsvHeader()
                ?: ""

        if (firstLine in NUBANK_CSV_HEADERS_NORMALIZED) {
            return ImportSource.CSV_NUBANK
        }

        val columns = parseColumns(firstLine)
        if (matchesItau(columns)) {
            return ImportSource.CSV_ITAU
        }
        if (matchesBradesco(columns)) {
            return ImportSource.CSV_BRADESCO
        }
        if (matchesSantander(columns)) {
            return ImportSource.CSV_SANTANDER
        }

        throw UnsupportedImportSourceException(
            "Cannot determine import source for file: $fileName",
        )
    }
}

private fun matchesItau(columns: List<String>): Boolean {
    val set = columns.toSet()
    return set.contains("historico") &&
        set.any { it == "data_lancamento" || it == "data" || it == "lancamento" } &&
        set.any { it == "valor" || it == "debito" || it == "credito" || it == "valor_lancamento" } &&
        set.intersect(ITAU_CSV_HEADER_HINTS).size >= 3
}

private fun matchesBradesco(columns: List<String>): Boolean {
    val set = columns.toSet()
    return set.contains("historico") &&
        set.contains("data") &&
        set.any { it.contains("docto") || it == "documento" } &&
        set.any { it.contains("credito") } &&
        set.any { it.contains("debito") } &&
        set.intersect(BRADESCO_CSV_HEADER_HINTS).size >= 3
}

private fun matchesSantander(columns: List<String>): Boolean {
    val set = columns.toSet()
    return set.contains("data") &&
        set.any { it == "hora" || it == "hora_lancamento" } &&
        set.any { it == "descricao" || it == "historico" || it == "descricao_lancamento" } &&
        set.any { it == "valor" || it == "valor_rs" } &&
        set.any { it.contains("saldo") } &&
        set.intersect(SANTANDER_CSV_HEADER_HINTS).size >= 4
}

private fun parseColumns(normalizedHeaderLine: String): List<String> {
    val delimiter =
        when {
            normalizedHeaderLine.count { it == ';' } > normalizedHeaderLine.count { it == ',' } -> ';'
            else -> ','
        }
    return normalizedHeaderLine.split(delimiter)
        .map { raw ->
            raw.trim()
                .replace("[^a-z0-9]+".toRegex(), "_")
                .replace("_+".toRegex(), "_")
                .trim('_')
        }
        .filter { it.isNotBlank() }
}

private fun String.normalizeCsvHeader(): String {
    val noBom = this.removePrefix("\uFEFF").trim()
    val normalized = Normalizer.normalize(noBom, Normalizer.Form.NFD)
    return normalized
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()
}
