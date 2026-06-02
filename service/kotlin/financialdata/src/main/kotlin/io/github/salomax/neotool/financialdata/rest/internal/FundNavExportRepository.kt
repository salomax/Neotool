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
class FundNavExportRepository(
    private val dataSource: DataSource,
    @param:Property(name = "financialdata.market-data-export.fund-navs.fetch-size", defaultValue = "1000")
    private val fetchSize: Int,
) {
    fun stream(
        from: LocalDate,
        to: LocalDate,
        cnpjs: Collection<String>?,
        updatedSince: Instant?,
        after: String?,
        consumer: (FundNavExportLine) -> Unit,
    ) {
        val normalizedCnpjs = cnpjs?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct()
        val hasCnpjs = !normalizedCnpjs.isNullOrEmpty()
        val decodedCursor = after?.let(CursorEncoder::decodeCompositeCursorToString)
        val hasCursor = decodedCursor != null

        val sql =
            """
            SELECT
                ia.id::text,
                ia.fund_cnpj,
                adp.price_date,
                adp.close_price,
                adp.created_at
            FROM financial_data.asset_daily_price adp
            JOIN financial_data.investment_asset ia ON ia.id = adp.asset_id
            WHERE ia.source = 'CVM'
              AND ia.fund_cnpj IS NOT NULL
              AND adp.price_date >= ?
              AND adp.price_date <= ?
              ${if (hasCnpjs) "AND ia.fund_cnpj = ANY (?)" else ""}
              ${if (updatedSince != null) "AND adp.created_at >= ?" else ""}
              ${
                if (hasCursor) {
                    "AND (adp.price_date > ? OR (adp.price_date = ? AND " +
                        "(ia.fund_cnpj > ? OR (ia.fund_cnpj = ? AND ia.id::text > ?))))"
                } else {
                    ""
                }
            }
            ORDER BY adp.price_date ASC, ia.fund_cnpj ASC, ia.id ASC
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
                if (hasCnpjs) {
                    val cnpjArray = connection.createArrayOf("text", normalizedCnpjs!!.toTypedArray())
                    try {
                        statement.setArray(index++, cnpjArray)
                        bindRemainingParameters(statement, index, updatedSince, decodedCursor)
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                consumer(resultSet.toExportLine())
                            }
                        }
                    } finally {
                        cnpjArray.free()
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
            val cursorCnpj = cursor.fieldValues["cnpj"] as String
            statement.setObject(index++, cursorDate)
            statement.setObject(index++, cursorDate)
            statement.setString(index++, cursorCnpj)
            statement.setString(index++, cursorCnpj)
            statement.setString(index, cursor.id)
        }
    }

    private fun java.sql.ResultSet.toExportLine(): FundNavExportLine {
        val assetId = getString(1)
        val cnpj = getString(2)
        val date = getDate(3).toLocalDate()
        return FundNavExportLine(
            cursor =
                CursorEncoder.encodeCompositeCursor(
                    fieldValues = mapOf("date" to date.toString(), "cnpj" to cnpj),
                    id = assetId,
                ),
            cnpj = cnpj,
            date = date,
            closePrice = getBigDecimal(4),
            updatedAt = getTimestamp(5).toInstant(),
        )
    }
}
