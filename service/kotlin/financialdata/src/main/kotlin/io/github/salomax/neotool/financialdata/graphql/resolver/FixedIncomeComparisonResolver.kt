package io.github.salomax.neotool.financialdata.graphql.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonInput
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeProduct
import io.github.salomax.neotool.financialdata.domain.fixedincome.ProductGroup
import io.github.salomax.neotool.financialdata.domain.fixedincome.RateModel
import io.github.salomax.neotool.financialdata.graphql.dto.FixedIncomeComparisonResultDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.FixedIncomeComparisonMapper
import io.github.salomax.neotool.financialdata.service.fixedincome.FixedIncomeComparisonService
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate

@Singleton
class FixedIncomeComparisonResolver(
    private val comparisonService: FixedIncomeComparisonService,
    private val mapper: FixedIncomeComparisonMapper,
) {
    fun fixedIncomeComparison(env: DataFetchingEnvironment): FixedIncomeComparisonResultDTO {
        @Suppress("UNCHECKED_CAST")
        val inputMap =
            env.getArgument<Map<String, Any?>>("input")
                ?: throw IllegalArgumentException("input is required")
        val input = parseInput(inputMap)
        val result = comparisonService.compare(input)
        return mapper.toResultDTO(result)
    }

    private fun parseInput(map: Map<String, Any?>): FixedIncomeComparisonInput {
        val productGroup =
            parseProductGroup(
                getString(map, "productGroup") ?: throw IllegalArgumentException("productGroup is required"),
            )
        val product = getString(map, "product")?.let(::parseProduct)
        val principal =
            toBigDecimal(getNumber(map, "principal") ?: throw IllegalArgumentException("principal is required"))
        val startDate = getDate(map, "startDate") ?: throw IllegalArgumentException("startDate is required")
        val endDate = getDate(map, "endDate") ?: throw IllegalArgumentException("endDate is required")
        val rateModel =
            parseRateModel(getString(map, "rateModel") ?: throw IllegalArgumentException("rateModel is required"))
        val contractedRateBps = getInt(map, "contractedRateBps")
        val cdiAnnualRateBps = getInt(map, "cdiAnnualRateBps")
        val ipcaAnnualRateBps = getInt(map, "ipcaAnnualRateBps")
        return FixedIncomeComparisonInput(
            productGroup = productGroup,
            product = product,
            principal = principal,
            startDate = startDate,
            endDate = endDate,
            rateModel = rateModel,
            contractedRateBps = contractedRateBps,
            cdiAnnualRateBps = cdiAnnualRateBps,
            ipcaAnnualRateBps = ipcaAnnualRateBps,
        )
    }

    private fun getString(
        map: Map<String, Any?>,
        key: String,
    ): String? = (map[key] as? String)?.takeIf { it.isNotBlank() }

    private fun getNumber(
        map: Map<String, Any?>,
        key: String,
    ): Number? =
        when (val v = map[key]) {
            is Number -> v
            is String -> v.toDoubleOrNull()
            else -> null
        }

    private fun getInt(
        map: Map<String, Any?>,
        key: String,
    ): Int? =
        when (val v = map[key]) {
            is Int -> v
            is Long -> v.toInt()
            is Short -> v.toInt()
            is Byte -> v.toInt()
            is Number -> {
                val doubleValue = v.toDouble()
                if (!doubleValue.isFinite() || doubleValue % 1.0 != 0.0) {
                    throw IllegalArgumentException("$key must be an integer bps")
                }
                doubleValue.toInt()
            }
            is String -> v.toIntOrNull()
            else -> null
        }

    private fun toBigDecimal(n: Number): BigDecimal = BigDecimal(n.toString())

    private fun getDate(
        map: Map<String, Any?>,
        key: String,
    ): LocalDate? {
        val v = map[key] ?: return null
        return when (v) {
            is String -> LocalDate.parse(v)
            is java.time.LocalDate -> v
            else -> v.toString().let { s -> runCatching { LocalDate.parse(s) }.getOrNull() }
        }
    }

    private fun parseProduct(s: String): FixedIncomeProduct =
        FixedIncomeProduct.entries.find { it.name == s }
            ?: throw IllegalArgumentException("Unknown product: $s")

    private fun parseProductGroup(s: String): ProductGroup =
        ProductGroup.entries.find { it.name == s }
            ?: throw IllegalArgumentException("Unknown productGroup: $s")

    private fun parseRateModel(s: String): RateModel =
        RateModel.entries.find { it.name == s }
            ?: throw IllegalArgumentException("Unknown rateModel: $s")
}
