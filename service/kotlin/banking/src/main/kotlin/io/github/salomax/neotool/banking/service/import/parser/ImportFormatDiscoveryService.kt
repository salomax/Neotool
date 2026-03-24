package io.github.salomax.neotool.banking.service.import.parser

import io.github.salomax.neotool.banking.domain.import.*
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.service.import.ImportSourceDetector
import io.github.salomax.neotool.banking.service.import.ImportTextDecoder
import io.github.salomax.neotool.banking.domain.import.isCsvSource

data class ImportFormatDiscoveryResult(
    val format: ImportFileFormat,
    val detectedSource: ImportSource?,
    val reason: String,
)

/**
 * Content-first format discovery to support source AUTO mode.
 */
class ImportFormatDiscoveryService {
    fun discover(
        bytes: ByteArray,
        fileName: String?,
    ): ImportFormatDiscoveryResult {
        if (looksLikeOfx(bytes)) {
            return ImportFormatDiscoveryResult(
                format = ImportFileFormat.OFX,
                detectedSource = ImportSource.OFX,
                reason = "ofx_signature",
            )
        }
        if (looksLikeXlsx(bytes)) {
            return ImportFormatDiscoveryResult(
                format = ImportFileFormat.XLSX,
                detectedSource = null,
                reason = "xlsx_zip_signature",
            )
        }

        val detected = runCatching { ImportSourceDetector.detect(bytes, fileName) }.getOrNull()
        if (detected != null) {
            return when {
                detected == ImportSource.OFX ->
                    ImportFormatDiscoveryResult(
                        format = ImportFileFormat.OFX,
                        detectedSource = ImportSource.OFX,
                        reason = "detector_ofx",
                    )
                detected.isCsvSource() ->
                    ImportFormatDiscoveryResult(
                        format = ImportFileFormat.CSV,
                        detectedSource = detected,
                        reason = "detector_csv",
                    )
                else ->
                    ImportFormatDiscoveryResult(
                        format = ImportFileFormat.UNKNOWN,
                        detectedSource = detected,
                        reason = "detector_non_file_source",
                    )
            }
        }

        val lowerName = fileName?.lowercase().orEmpty()
        if (lowerName.endsWith(".ofx")) {
            return ImportFormatDiscoveryResult(
                format = ImportFileFormat.OFX,
                detectedSource = ImportSource.OFX,
                reason = "extension_ofx",
            )
        }
        if (lowerName.endsWith(".csv")) {
            return ImportFormatDiscoveryResult(
                format = ImportFileFormat.CSV,
                detectedSource = ImportSource.CSV_AUTO,
                reason = "extension_csv",
            )
        }
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            return ImportFormatDiscoveryResult(
                format = ImportFileFormat.XLSX,
                detectedSource = null,
                reason = "extension_xlsx",
            )
        }
        if (looksLikeCsv(bytes)) {
            return ImportFormatDiscoveryResult(
                format = ImportFileFormat.CSV,
                detectedSource = ImportSource.CSV_AUTO,
                reason = "csv_heuristic",
            )
        }
        return ImportFormatDiscoveryResult(
            format = ImportFileFormat.UNKNOWN,
            detectedSource = null,
            reason = "unknown",
        )
    }

    private fun looksLikeOfx(bytes: ByteArray): Boolean {
        val peek = ImportTextDecoder.decode(bytes.take(256).toByteArray()).text.lowercase()
        return peek.contains("ofxheader:") || peek.contains("<ofx")
    }

    /**
     * XLSX is a ZIP container and usually starts with PK\x03\x04.
     */
    private fun looksLikeXlsx(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

    private fun looksLikeCsv(bytes: ByteArray): Boolean {
        val preview = ImportTextDecoder.decode(bytes.take(4096).toByteArray()).text
        val lines = preview.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.take(3).toList()
        if (lines.isEmpty()) return false
        val first = lines.first()
        val delimiterCount = first.count { it == ',' || it == ';' || it == '\t' || it == '|' }
        return delimiterCount >= 1
    }
}
