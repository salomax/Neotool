package io.github.salomax.neotool.banking.model.domain.investment.validation

object CnpjValidator {
    fun normalize(value: String): String = value.filter(Char::isDigit)

    fun isValid(value: String): Boolean {
        val digits = normalize(value)
        if (digits.length != 14) return false
        if (digits.all { it == digits.first() }) return false

        val firstCheck = calculateCheckDigit(digits.substring(0, 12), intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2))
        val secondCheck = calculateCheckDigit(digits.substring(0, 12) + firstCheck, intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2))

        return digits == digits.substring(0, 12) + firstCheck + secondCheck
    }

    private fun calculateCheckDigit(
        digits: String,
        weights: IntArray,
    ): Int {
        val sum = digits.mapIndexed { index, c -> c.digitToInt() * weights[index] }.sum()
        val remainder = sum % 11
        return if (remainder < 2) 0 else 11 - remainder
    }
}
