package io.github.salomax.neotool.banking.graphql.dto

import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJobStatus
import java.time.Instant
import java.util.UUID

data class PdfImportJobDTO(
    val id: UUID,
    val status: PdfImportJobStatus,
    val fileName: String?,
    val createdAt: Instant,
    val reviewPayload: Any?,
    val errorMessage: String?,
)

