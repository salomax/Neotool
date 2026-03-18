package io.github.salomax.neotool.banking.domain.investment

import java.math.BigDecimal
import java.time.LocalDate

interface IndicatorRateProvider {
    /**
     * Returns latest annual rates for supported indexers (CDI, SELIC, IPCA, IGP_M, IGP_DI, INPC, TR, DI).
     * Map keys are [Indexer] values; values are decimal annual rates (e.g. 0.1325 = 13.25% a.a.).
     */
    fun getLatestRates(): Map<Indexer, BigDecimal>

    /**
     * Reference date of the underlying indicator snapshot, when available.
     * Returns null when no indicator data is available.
     */
    fun getReferenceDate(): LocalDate?
}

