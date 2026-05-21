package io.github.salomax.neotool.financialdata.rest.internal

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

@Singleton
class ListedAssetDailyPriceExportRepository(
    private val dataSource: DataSource,
    @param:Property(name = "financialdata.market-data-export.listed-prices.fetch-size", defaultValue = "1000")
    private val fetchSize: Int,
) {
    fun stream(
        from: LocalDate,
        to: LocalDate,
        tickers: Collection<String>?,
        updatedSince: Instant?,
        after: String?,
        consumer: (ListedAssetDailyPriceExportLine) -> Unit,
    ) {
        val normalizedTickers = tickers?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }?.distinct()
        val hasTickers = !normalizedTickers.isNullOrEmpty()
        val decodedCursor = after?.let(CursorEncoder::decodeCompositeCursorToString)
        val hasCursor = decodedCursor != null

        val sql =
            """
            SELECT
                ia.id::text,
                upper(ia.ticker),
                ia.isin_code,
                adp.price_date,
                adp.close_price,
                adp.created_at
            FROM financial_data.asset_daily_price adp
            JOIN financial_data.investment_asset ia ON ia.id = adp.asset_id
            WHERE ia.source = 'B3'
              AND ia.ticker IS NOT NULL
              AND adp.price_date >= ?
              AND adp.price_date <= ?
              ${if (hasTickers) "AND upper(ia.ticker) = ANY (?)" else ""}
              ${if (updatedSince != null) "AND adp.created_at >= ?" else ""}
              ${
                if (hasCursor) {
                    "AND (adp.price_date > ? OR (adp.price_date = ? AND " +
                        "(upper(ia.ticker) > ? OR (upper(ia.ticker) = ? AND ia.id::text > ?))))"
                } else {
                    ""
                }
            }
            ORDER BY adp.price_date ASC, upper(ia.ticker) ASC, ia.id ASC
            """.trimIndent()

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.isReadOnly = true
            connection.prepareStatement(
                sql,
                java.sql.ResultSet.TYPE_FORWARD_ONLY,
                java.sql.ResultSet.CONCUR_READ_ONLY,
            ).use { statement ->
                statement.fetchSize = fetchSize.coerceAtLeast(1)
                var index = 1
                statement.setObject(index++, from)
                statement.setObject(index++, to)
                if (hasTickers) {
                    val tickerArray = connection.createArrayOf("text", normalizedTickers!!.toTypedArray())
                    try {
                        statement.setArray(index++, tickerArray)
                        bindRemainingParameters(statement, index, updatedSince, decodedCursor)
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                consumer(resultSet.toExportLine())
                            }
                        }
                    } finally {
                        tickerArray.free()
                    }
                } else {
                    bindRemainingParameters(statement, index, updatedSince, decodedCursor)
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            consumer(resultSet.toExportLine())
                        }
                    }
                }
            }
        }
    }

    private fun bindRemainingParameters(
        statement: java.sql.PreparedStatement,
        startIndex: Int,
        updatedSince: Instant?,
        cursor: CompositeCursor?,
    ) {
        var index = startIndex
        if (updatedSince != null) {
            statement.setTimestamp(index++, Timestamp.from(updatedSince))
        }
        if (cursor != null) {
            val cursorDate = LocalDate.parse(cursor.fieldValues["date"] as String)
            val cursorTicker = cursor.fieldValues["ticker"] as String
            statement.setObject(index++, cursorDate)
            statement.setObject(index++, cursorDate)
            statement.setString(index++, cursorTicker)
            statement.setString(index++, cursorTicker)
            statement.setString(index, cursor.id)
        }
    }

    private fun java.sql.ResultSet.toExportLine(): ListedAssetDailyPriceExportLine {
        val assetId = getString(1)
        val ticker = getString(2)
        val date = getDate(4).toLocalDate()
        return ListedAssetDailyPriceExportLine(
            cursor =
                CursorEncoder.encodeCompositeCursor(
                    fieldValues = mapOf("date" to date.toString(), "ticker" to ticker),
                    id = assetId,
                ),
            ticker = ticker,
            isinCode = getString(3),
            date = date,
            closePrice = getBigDecimal(5),
            updatedAt = getTimestamp(6).toInstant(),
        )
    }
}
