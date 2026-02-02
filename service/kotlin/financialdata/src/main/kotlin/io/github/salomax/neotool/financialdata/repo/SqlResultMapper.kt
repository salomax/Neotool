package io.github.salomax.neotool.financialdata.repo

import java.math.BigDecimal
import java.util.UUID

/**
 * Utility class for mapping SQL query results by column name.
 * Provides type-safe accessors for common data types from native query results.
 *
 * This class eliminates the need to use numeric indices when mapping SQL results,
 * making the code more maintainable and less error-prone.
 *
 * Usage:
 * ```kotlin
 * val columnIndices = getColumnIndexMap() // Map of column names to indices
 * val results = nativeQuery.resultList as List<Array<Any?>>
 *
 * results.map { row ->
 *     val mapper = SqlResultMapper(row as Array<Any?>, columnIndices)
 *     val name = mapper.getString("name")
 *     val age = mapper.getInt("age")
 *     val price = mapper.getNumber("price")
 * }
 * ```
 */
class SqlResultMapper(
    private val row: Array<Any?>,
    private val columnIndices: Map<String, Int>,
) {
    /**
     * Gets the raw value from the specified column.
     */
    fun getValue(columnName: String): Any? {
        val index =
            columnIndices[columnName]
                ?: throw IllegalArgumentException("Column '$columnName' not found in column indices")
        return row[index]
    }

    /**
     * Gets a String value from the specified column.
     * Returns null if the value is null or cannot be cast to String.
     */
    fun getString(columnName: String): String? {
        return getValue(columnName) as? String
    }

    /**
     * Gets a Char value from the specified column.
     * Note: Even though the database type may be CHAR(1), it's returned as String,
     * so we extract the first character from the string.
     * Returns null if the value is null, empty, or cannot be converted.
     */
    fun getChar(columnName: String): Char? {
        val value = getValue(columnName) ?: return null
        return when (value) {
            is Char -> value
            is String -> value.firstOrNull()
            else -> null
        }
    }

    /**
     * Gets a BigDecimal value from the specified column.
     * Converts Number types to BigDecimal for precise decimal handling.
     * Returns null if the value is null or cannot be converted.
     */
    fun getNumber(columnName: String): BigDecimal? {
        return (getValue(columnName) as? Number)?.let { BigDecimal(it.toString()) }
    }

    /**
     * Gets an Int value from the specified column.
     * Returns null if the value is null or cannot be converted.
     */
    fun getInt(columnName: String): Int? {
        return (getValue(columnName) as? Number)?.toInt()
    }

    /**
     * Gets a Long value from the specified column.
     * Returns null if the value is null or cannot be converted.
     */
    fun getLong(columnName: String): Long? {
        return (getValue(columnName) as? Number)?.toLong()
    }

    /**
     * Gets a Boolean value from the specified column.
     * Returns null if the value is null or cannot be converted.
     */
    fun getBoolean(columnName: String): Boolean? {
        return getValue(columnName) as? Boolean
    }

    /**
     * Gets a UUID value from the specified column.
     * Handles both UUID objects and String representations.
     * Returns null if the value is null or cannot be converted.
     */
    fun getUUID(columnName: String): UUID? {
        val value = getValue(columnName) ?: return null
        return when (value) {
            is UUID -> value
            is String ->
                try {
                    UUID.fromString(value)
                } catch (e: IllegalArgumentException) {
                    null
                }
            else -> null
        }
    }
}
