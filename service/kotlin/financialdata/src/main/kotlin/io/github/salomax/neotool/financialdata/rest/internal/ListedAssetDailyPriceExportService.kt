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
open class ListedAssetDailyPriceExportService(
    private val exportRepository: ListedAssetDailyPriceExportRepository,
    private val objectMapper: ObjectMapper,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    open fun writeNdjson(
        outputStream: OutputStream,
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant?,
        tickers: Collection<String>?,
        after: String?,
        gzipEnabled: Boolean,
    ) {
        val rawOutput = if (gzipEnabled) GZIPOutputStream(outputStream, true) else outputStream
        var emittedRows = 0
        OutputStreamWriter(rawOutput, Charsets.UTF_8).buffered().use { writer ->
            exportRepository.stream(
                from = from,
                to = to,
                tickers = tickers,
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
            "Listed asset daily price export completed: from=$from to=$to tickers=${tickers?.size ?: 0} " +
                "after=${after != null} rows=$emittedRows gzip=$gzipEnabled"
        }
    }
}
