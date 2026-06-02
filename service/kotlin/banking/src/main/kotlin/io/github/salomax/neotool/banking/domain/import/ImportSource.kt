package io.github.salomax.neotool.banking.domain.import

import io.micronaut.serde.annotation.Serdeable

/**
 * Import channel for transaction_imports.
 */
@Serdeable
enum class ImportSource {
    AUTO,
    OFX,
    CSV_AUTO,
    CSV_NUBANK,
    CSV_ITAU,
    CSV_BRADESCO,
    CSV_SANTANDER,
    NFC_E,
    RECEIPT_OCR,
    OPEN_FINANCE,
    PLUGGY,
}

fun ImportSource.isCsvSource(): Boolean =
    this == ImportSource.CSV_AUTO ||
        this == ImportSource.CSV_NUBANK ||
        this == ImportSource.CSV_ITAU ||
        this == ImportSource.CSV_BRADESCO ||
        this == ImportSource.CSV_SANTANDER

fun ImportSource.isFileImportSource(): Boolean =
    this == ImportSource.AUTO ||
        this == ImportSource.OFX ||
        this.isCsvSource()
