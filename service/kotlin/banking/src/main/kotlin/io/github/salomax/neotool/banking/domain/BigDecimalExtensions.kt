package io.github.salomax.neotool.banking.domain

import java.math.BigDecimal

fun Any?.toBigDecimalOrZero(): BigDecimal =
    when (this) {
        null -> BigDecimal.ZERO
        is BigDecimal -> this
        is Number -> BigDecimal(this.toString())
        else -> this.toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
