package io.github.salomax.neotool.financialdata.repo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.repo.result.BacenBankReportDataResult
import io.github.salomax.neotool.financialdata.repo.result.FinancialInstitutionDataResult
import io.github.salomax.neotool.financialdata.repo.result.ReportDescriptorResult
import io.github.salomax.neotool.financialdata.repo.result.ReportValueResult
import io.github.salomax.neotool.financialdata.service.QuarterService
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Custom repository implementation for BacenInstitution detail operations.
 * Uses native SQL queries for complex data aggregation and joins.
 */
@Singleton
open class BacenInstitutionDetailRepositoryImpl(
    private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper,
    private val quarterService: QuarterService,
) : BacenInstitutionDetailRepositoryCustom {

    @ReadOnly
    override fun getFinancialInstitutionData(id: String, anoMes: String?): FinancialInstitutionDataResult? {
        // Resolve quarter: use provided anoMes or latest quarter globally
        val targetAnoMes = anoMes ?: quarterService.getLatestQuarter() ?: return null

        val sql = """
            SELECT
                bi.nome_instituicao,
                bi.tipo_controle,
                bi.tipo_consolidacao,
                bi.tipo_consolidado_bancario,
                fi.legal_name,
                fi.trade_name,
                fi.asset_id,
                fi.website,
                fi.social_network,
                fi.receita_data
            FROM financial_data.bacen_institution bi
                LEFT JOIN financial_data.financial_institution_bacen_institution fibi
                    ON fibi.bacen_cod_inst = bi.bacen_cod_inst
                    AND fibi.ano_mes_ref = bi.ano_mes_ref
                LEFT JOIN financial_data.financial_institution fi
                    ON fi.id = fibi.financial_institution_id
            WHERE bi.bacen_cod_inst = ?
              AND bi.ano_mes_ref = ?
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter(1, id)
        query.setParameter(2, targetAnoMes)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as? List<Array<Any?>>

        if (results.isNullOrEmpty()) {
            return null
        }

        val row = results[0]
        val columnIndices = mapOf(
            "nome_instituicao" to 0,
            "tipo_controle" to 1,
            "tipo_consolidacao" to 2,
            "tipo_consolidado_bancario" to 3,
            "legal_name" to 4,
            "trade_name" to 5,
            "asset_id" to 6,
            "website" to 7,
            "social_network" to 8,
            "receita_data" to 9,
        )

        val mapper = SqlResultMapper(row, columnIndices)

        // Parse social_network JSON
        val socialNetworksJson = mapper.getValue("social_network") as? String
        val socialNetworks = parseSocialNetworks(socialNetworksJson)

        // Parse receita_data JSON
        val receitaDataJson = mapper.getValue("receita_data") as? String
        val (mainActivity, address, founded) = parseReceitaData(receitaDataJson)

        return FinancialInstitutionDataResult(
            nomeInstituicao = mapper.getString("nome_instituicao"),
            tipoControle = mapper.getString("tipo_controle"),
            tipoConsolidacao = mapper.getString("tipo_consolidacao"),
            tipoConsolidadoBancario = mapper.getString("tipo_consolidado_bancario"),
            legalName = mapper.getString("legal_name"),
            tradeName = mapper.getString("trade_name"),
            assetId = mapper.getUUID("asset_id"),
            website = mapper.getString("website"),
            socialNetworks = socialNetworks,
            mainActivity = mainActivity,
            address = address,
            founded = founded,
        )
    }

    @ReadOnly
    override fun getBacenBankReportData(id: String, anoMes: String?): List<BacenBankReportDataResult> {
        // Resolve quarter: use provided anoMes or latest quarter globally
        val targetAnoMes = anoMes ?: quarterService.getLatestQuarter() ?: return emptyList()

        val sql = """
            SELECT
                bi.bacen_cod_inst,
                bi.ano_mes_ref,
                br.numero_relatorio,
                bbrc.nome_relatorio,
                bra.grupo,
                bra.conta,
                bra.nome_coluna,
                brd.saldo
            FROM financial_data.bacen_institution bi
                INNER JOIN financial_data.bacen_bank_report br
                    ON bi.bacen_cod_inst = br.cod_inst
                    AND bi.ano_mes_ref = br.ano_mes
                INNER JOIN financial_data.bacen_bank_report_data brd
                    ON br.id = brd.bank_report_id
                INNER JOIN financial_data.bacen_bank_report_account bra
                    ON brd.account_id = bra.id
                INNER JOIN financial_data.bacen_bank_report_catalog bbrc
                    ON bbrc.numero_relatorio = br.numero_relatorio
            WHERE 1=1
                AND bi.bacen_cod_inst = ?
                AND bi.ano_mes_ref = ?
            GROUP BY
                bi.bacen_cod_inst,
                bi.ano_mes_ref,
                br.numero_relatorio,
                bbrc.nome_relatorio,
                bra.grupo,
                bra.conta,
                bra.nome_coluna,
                brd.saldo
            ORDER BY
                bi.bacen_cod_inst,
                bi.ano_mes_ref,
                bi.nome_instituicao,
                br.numero_relatorio,
                bra.grupo,
                bra.conta
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter(1, id)
        query.setParameter(2, targetAnoMes)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as? List<Array<Any?>> ?: return emptyList()

        val columnIndices = mapOf(
            "bacen_cod_inst" to 0,
            "ano_mes_ref" to 1,
            "numero_relatorio" to 2,
            "nome_relatorio" to 3,
            "grupo" to 4,
            "conta" to 5,
            "nome_coluna" to 6,
            "saldo" to 7,
        )

        return results.map { row ->
            val mapper = SqlResultMapper(row, columnIndices)
            BacenBankReportDataResult(
                bacenCodInst = mapper.getString("bacen_cod_inst") ?: id,
                anoMesRef = mapper.getString("ano_mes_ref") ?: targetAnoMes,
                reportNumber = mapper.getString("numero_relatorio") ?: "",
                reportName = mapper.getString("nome_relatorio"),
                group = mapper.getString("grupo"),
                account = mapper.getString("conta"),
                columnName = mapper.getString("nome_coluna"),
                balance = mapper.getNumber("saldo"),
            )
        }
    }

    @ReadOnly
    override fun getLatestQuarter(id: String): String? {
        val sql = """
            SELECT DISTINCT ON (cod_inst)
                ano_mes
            FROM financial_data.bacen_bank_report
            WHERE cod_inst = ?
            ORDER BY cod_inst, ano_mes DESC
            LIMIT 1
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter(1, id)

        @Suppress("UNCHECKED_CAST")
        val result = query.singleResult as? String

        return result
    }

    @ReadOnly
    override fun getReportsByQuarterRange(
        id: String,
        range: QuarterRange,
    ): List<ReportDescriptorResult> {
        val quarters = calculateQuarterRange(id, range)

        if (quarters.isEmpty()) {
            return emptyList()
        }

        val quartersPlaceholder = quarters.joinToString(",") { "?" }
        val sql = """
            SELECT DISTINCT
                br.ano_mes as quarter,
                br.numero_relatorio as report_id,
                brc.nome_relatorio as name
            FROM financial_data.bacen_bank_report br
                INNER JOIN financial_data.bacen_bank_report_catalog brc
                    ON brc.numero_relatorio = br.numero_relatorio
            WHERE br.cod_inst = ?
                AND br.ano_mes IN ($quartersPlaceholder)
            ORDER BY br.ano_mes DESC, br.numero_relatorio
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter(1, id)
        quarters.forEachIndexed { index, quarter ->
            query.setParameter(index + 2, quarter)
        }

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as? List<Array<Any?>> ?: return emptyList()

        val columnIndices = mapOf(
            "quarter" to 0,
            "report_id" to 1,
            "name" to 2,
        )

        return results.map { row ->
            val mapper = SqlResultMapper(row, columnIndices)
            ReportDescriptorResult(
                quarter = mapper.getString("quarter") ?: "",
                reportId = mapper.getString("report_id") ?: "",
                name = mapper.getString("name"),
                group = null,  // Not available in summary - only in reportDetails
                accountNumber = null,  // Not available in summary - only in reportDetails
                columnName = null,  // Not available in summary - only in reportDetails
            )
        }
    }

    @ReadOnly
    override fun getReportDetailsByQuarterRange(
        id: String,
        reportId: String,
        range: QuarterRange,
    ): List<ReportValueResult> {
        val quarters = calculateQuarterRange(id, range)

        if (quarters.isEmpty()) {
            return emptyList()
        }

        val quartersPlaceholder = quarters.joinToString(",") { "?" }
        val sql = """
            SELECT
                br.ano_mes as quarter,
                br.numero_relatorio as report_id,
                brc.nome_relatorio as name,
                bra.grupo,
                bra.conta as account_number,
                bra.nome_coluna as column_name,
                brd.saldo as value
            FROM financial_data.bacen_bank_report br
                INNER JOIN financial_data.bacen_bank_report_catalog brc
                    ON brc.numero_relatorio = br.numero_relatorio
                INNER JOIN financial_data.bacen_bank_report_data brd
                    ON brd.bank_report_id = br.id
                INNER JOIN financial_data.bacen_bank_report_account bra
                    ON bra.id = brd.account_id
            WHERE br.cod_inst = ?
                AND br.numero_relatorio = ?
                AND br.ano_mes IN ($quartersPlaceholder)
            ORDER BY br.ano_mes DESC, bra.grupo, bra.conta
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter(1, id)
        query.setParameter(2, reportId)
        quarters.forEachIndexed { index, quarter ->
            query.setParameter(index + 3, quarter)
        }

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as? List<Array<Any?>> ?: return emptyList()

        val columnIndices = mapOf(
            "quarter" to 0,
            "report_id" to 1,
            "name" to 2,
            "grupo" to 3,
            "account_number" to 4,
            "column_name" to 5,
            "value" to 6,
        )

        return results.map { row ->
            val mapper = SqlResultMapper(row, columnIndices)
            ReportValueResult(
                quarter = mapper.getString("quarter") ?: "",
                reportId = mapper.getString("report_id") ?: "",
                name = mapper.getString("name"),
                group = mapper.getString("grupo"),
                accountNumber = mapper.getString("account_number"),
                columnName = mapper.getString("column_name"),
                value = mapper.getNumber("value"),
            )
        }
    }

    /**
     * Calculate the list of quarters based on the range specification for a specific institution.
     * Returns quarters in descending order (most recent first).
     */
    private fun calculateQuarterRange(id: String, range: QuarterRange): List<String> {
        return when {
            range.last != null -> {
                // Get last N quarters for this specific institution
                val sql = """
                    SELECT DISTINCT ano_mes
                    FROM financial_data.bacen_bank_report
                    WHERE cod_inst = ?
                    ORDER BY ano_mes DESC
                    LIMIT ?
                """.trimIndent()

                val query = entityManager.createNativeQuery(sql)
                query.setParameter(1, id)
                query.setParameter(2, range.last)

                @Suppress("UNCHECKED_CAST")
                (query.resultList as? List<String>) ?: emptyList()
            }
            range.from != null && range.to != null -> {
                // Generate quarters in range
                val fromYear = range.from.substring(0, 4).toInt()
                val fromMonth = range.from.substring(4, 6).toInt()
                val toYear = range.to.substring(0, 4).toInt()
                val toMonth = range.to.substring(4, 6).toInt()

                val quarters = mutableListOf<String>()
                var currentYear = fromYear
                var currentMonth = fromMonth

                while (currentYear < toYear || (currentYear == toYear && currentMonth <= toMonth)) {
                    quarters.add(String.format("%04d%02d", currentYear, currentMonth))
                    currentMonth += 3 // Next quarter
                    if (currentMonth > 12) {
                        currentMonth = 1
                        currentYear++
                    }
                }

                quarters.reversed() // Most recent first
            }
            else -> emptyList()
        }
    }

    /**
     * Parse social_network JSON array to List<String>.
     */
    private fun parseSocialNetworks(json: String?): List<String>? {
        if (json.isNullOrBlank()) {
            return null
        }

        return try {
            val node = objectMapper.readTree(json)
            if (node.isArray) {
                node.mapNotNull { it.asText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse receita_data JSON to extract mainActivity, address, and founded.
     * Returns triple: (mainActivity, address, founded)
     */
    private fun parseReceitaData(json: String?): Triple<String?, String?, String?> {
        if (json.isNullOrBlank()) {
            return Triple(null, null, null)
        }

        return try {
            val node = objectMapper.readTree(json)
            val mainActivity = node.get("mainActivity")?.get("text")?.asText()
            val addressNode = node.get("address")
            val city = addressNode?.get("city")?.asText()
            val state = addressNode?.get("state")?.asText()
            val address = if (city != null && state != null) {
                "$city, $state"
            } else {
                null
            }
            val foundedStr = node.get("founded")?.asText()
            val founded = foundedStr?.let { formatDate(it) }

            Triple(mainActivity, address, founded)
        } catch (e: Exception) {
            Triple(null, null, null)
        }
    }

    /**
     * Format date string to locale date format.
     * Assumes input is in ISO format (YYYY-MM-DD) or similar.
     */
    private fun formatDate(dateStr: String): String? {
        return try {
            // Try ISO format first
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE)
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            // If parsing fails, return as-is
            dateStr
        }
    }
}
