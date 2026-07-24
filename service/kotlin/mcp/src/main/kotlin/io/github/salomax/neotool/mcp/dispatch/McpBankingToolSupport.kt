package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.mcp.protocol.McpToolExecutionException
import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.render.McpToolSuccessResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.UUID

abstract class AbstractMcpBankingToolHandler(
    private val bankingClient: McpBankingGraphQLClient,
) : McpToolHandler {
    protected fun queryField(
        fieldName: String,
        query: String,
        variables: Map<String, Any>,
        principal: McpPrincipal,
    ): Any? {
        val response =
            try {
                runBlocking {
                    bankingClient.query(
                        query = query,
                        variables = variables,
                        principal = principal,
                    )
                }
            } catch (e: DomainException) {
                throw e
            } catch (e: McpToolExecutionException) {
                throw e
            }

        response.errors?.firstOrNull()?.let { throw it.toToolException() }
        return response.data?.get(fieldName)
    }

    protected fun success(payload: Pair<String, Any?>): McpToolResult =
        McpToolSuccessResult(mapOf(payload.first to payload.second.normalize().attachBrCurrencyCode()))

    protected fun Map<String, Any?>.assertOnlySupportedArguments(allowed: Set<String>) {
        val unsupported = keys.filterNot { it in allowed }
        if (unsupported.isNotEmpty()) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = unsupported.first(),
                parameters = mapOf("field" to unsupported.first()),
            )
        }
    }

    protected fun Map<String, Any?>.stringArgument(name: String): String? =
        this[name]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    protected fun Map<String, Any?>.requiredStringArgument(name: String): String =
        stringArgument(name)
            ?: throw ValidationException(CommonErrorCode.INVALID_INPUT, field = name, parameters = mapOf("field" to name))

    protected fun Map<String, Any?>.intArgument(name: String): Int? =
        when (val value = this[name]) {
            null -> null
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else ->
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = name,
                    parameters = mapOf("field" to name),
                )
        }

    protected fun Map<String, Any?>.booleanArgument(name: String): Boolean? =
        when (val value = this[name]) {
            null -> null
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        } ?: this[name]?.let {
            throw ValidationException(CommonErrorCode.INVALID_INPUT, field = name, parameters = mapOf("field" to name))
        }

    protected fun Map<String, Any?>.stringListArgument(name: String): List<String>? =
        when (val value = this[name]) {
            null -> null
            is List<*> ->
                value.map {
                    it?.toString()?.trim()?.takeIf(String::isNotEmpty)
                        ?: throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = name,
                            parameters = mapOf("field" to name),
                        )
                }
            is String -> listOf(value.trim()).filter { it.isNotEmpty() }
            else -> throw ValidationException(CommonErrorCode.INVALID_INPUT, field = name, parameters = mapOf("field" to name))
        }?.takeIf { it.isNotEmpty() }

    protected fun Map<String, Any?>.uuidArgument(name: String): String? =
        stringArgument(name)?.also {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                throw ValidationException(CommonErrorCode.INVALID_INPUT, field = name, parameters = mapOf("field" to name))
            }
        }

    protected fun Map<String, Any?>.requiredUuidArgument(name: String): String =
        uuidArgument(name)
            ?: throw ValidationException(CommonErrorCode.INVALID_INPUT, field = name, parameters = mapOf("field" to name))

    protected fun Map<String, Any?>.dateArgument(name: String): String? =
        stringArgument(name)?.also {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                throw ValidationException(CommonErrorCode.INVALID_INPUT, field = name, parameters = mapOf("field" to name))
            }
        }

    protected fun Map<String, Any?>.enumArgument(
        name: String,
        allowed: Set<String>,
    ): String? =
        stringArgument(name)?.uppercase()?.also {
            if (it !in allowed) {
                throw ValidationException(CommonErrorCode.INVALID_INPUT, field = name, parameters = mapOf("field" to name))
            }
        }

    protected fun validateMonth(
        name: String,
        value: Int?,
    ): Int? =
        value?.also {
            if (it !in 1..12) {
                throw ValidationException(
                    CommonErrorCode.VALIDATION_ERROR,
                    field = name,
                    parameters = mapOf("field" to name, "month" to it, "min" to 1, "max" to 12),
                )
            }
        }

    protected fun validateYear(
        name: String,
        value: Int?,
    ): Int? =
        value?.also {
            if (it !in 1900..2200) {
                throw ValidationException(
                    CommonErrorCode.VALIDATION_ERROR,
                    field = name,
                    parameters = mapOf("field" to name, "year" to it, "min" to 1900, "max" to 2200),
                )
            }
        }

    protected fun validateYearMonthRange(
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ) {
        val start = YearMonth.of(startYear, startMonth)
        val end = YearMonth.of(endYear, endMonth)
        if (end.isBefore(start)) {
            throw ValidationException(
                CommonErrorCode.VALIDATION_ERROR,
                field = "endMonth",
                parameters = mapOf("field" to "endMonth", "month" to endMonth, "year" to endYear),
            )
        }
    }

    protected fun notFound(
        resourceType: String,
        id: String,
    ): Nothing = throw NotFoundException(CommonErrorCode.RESOURCE_NOT_FOUND, resourceType, id)

    private fun Map<String, Any?>.toToolException(): McpToolExecutionException {
        val extensions = this["extensions"] as? Map<*, *> ?: emptyMap<Any, Any?>()
        val code = extensions["code"]?.toString()?.takeIf { it.isNotBlank() } ?: "BANKING_GRAPHQL_ERROR"
        val field =
            when (val rawField = extensions["field"]) {
                is List<*> -> rawField.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                is String -> rawField.split(".").filter { it.isNotBlank() }
                else -> null
            }
        val parameters =
            (extensions["parameters"] as? Map<*, *>)
                ?.entries
                ?.associate { (key, value) -> key.toString() to value }
                ?: emptyMap()

        return McpToolExecutionException(
            code = code,
            service = "banking",
            field = field?.takeIf { it.isNotEmpty() },
            parameters = parameters,
        )
    }

    private fun Any?.normalize(): Any? =
        when (this) {
            is Map<*, *> -> entries.associate { (key, value) -> key.toString() to value.normalize() }
            is List<*> -> map { it.normalize() }
            else -> this
        }

    private fun Any?.attachBrCurrencyCode(): Any? =
        when (this) {
            is Map<*, *> -> {
                val normalized =
                    entries.associate { (key, value) ->
                        key.toString() to value.attachBrCurrencyCode()
                    }.toMutableMap()
                if ("currencyCode" !in normalized && normalized.keys.any { it in MONETARY_FIELDS }) {
                    normalized["currencyCode"] = "BRL"
                }
                normalized
            }
            is List<*> -> map { it.attachBrCurrencyCode() }
            else -> this
        }

    private companion object {
        private val MONETARY_FIELDS =
            setOf(
                "amount",
                "amountBrl",
                "totalAmount",
                "totalIncome",
                "totalExpenses",
                "closingBalance",
                "openingBalance",
                "transfersIn",
                "transfersOut",
                "projectedClosingBalance",
                "net",
                "avgMonthlyIncome",
                "avgMonthlyExpenses",
                "avgMonthlySavings",
                "closingCashBalance",
                "closingPortfolioValue",
                "totalNetWorth",
                "totalInvested",
                "avgMonthlyInvested",
                "totalReturn",
                "avgMonthlyReturn",
                "openingPortfolioValue",
                "minimumPayment",
            )
    }
}
