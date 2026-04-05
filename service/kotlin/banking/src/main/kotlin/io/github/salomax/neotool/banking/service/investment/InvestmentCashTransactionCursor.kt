package io.github.salomax.neotool.banking.service.investment

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

/** Keyset cursor for [investmentCashTransactions][io.github.salomax.neotool.banking.graphql.investment.InvestmentResolver] (date, id). */
object InvestmentCashTransactionCursor {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(
        date: LocalDate,
        id: UUID,
    ): String {
        val raw = "$date|$id"
        return encoder.encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(cursor: String): Pair<LocalDate, UUID> {
        val raw = String(decoder.decode(cursor), StandardCharsets.UTF_8)
        val idx = raw.indexOf('|')
        require(idx > 0) { "Invalid investment cash transaction cursor" }
        val date = LocalDate.parse(raw.substring(0, idx))
        val id = UUID.fromString(raw.substring(idx + 1))
        return date to id
    }
}
