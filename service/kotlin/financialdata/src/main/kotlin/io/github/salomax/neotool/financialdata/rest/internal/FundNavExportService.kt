package io.github.salomax.neotool.financialdata.rest.internal

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

@Singleton
open class FundNavExportService(
    private val exportRepository: FundNavExportRepository,
    private val objectMapper: ObjectMapper,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    open fun writeNdjson(
        outputStream: OutputStream,
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant?,
        cnpjs: Collection<String>?,
        after: String?,
        gzipEnabled: Boolean,
    ) {
        val rawOutput = if (gzipEnabled) GZIPOutputStream(outputStream, true) else outputStream
        var emittedRows = 0
        OutputStreamWriter(rawOutput, Charsets.UTF_8).buffered().use { writer ->
            exportRepository.stream(
                from = from,
                to = to,
                cnpjs = cnpjs,
                updatedSince = updatedSince,
                after = after,
            ) { line ->
                writer.write(objectMapper.writeValueAsString(line))
                writer.write('\n'.code)
                emittedRows += 1
            }
            writer.flush()
            if (rawOutput is GZIPOutputStream) {
                rawOutput.finish()
            }
        }
        logger.info {
            "Fund NAV export completed: from=$from to=$to cnpjs=${cnpjs?.size ?: 0} " +
                "after=${after != null} rows=$emittedRows gzip=$gzipEnabled"
        }
    }
}
