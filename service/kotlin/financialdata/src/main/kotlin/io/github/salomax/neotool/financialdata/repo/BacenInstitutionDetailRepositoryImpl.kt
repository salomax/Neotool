package io.github.salomax.neotool.financialdata.repo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.repo.result.InstitutionDetailResult
import io.github.salomax.neotool.financialdata.repo.result.ReportDescriptorResult
import io.github.salomax.neotool.financialdata.repo.result.ReportValueResult
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
) : BacenInstitutionDetailRepositoryCustom {

    /**
     * Gets the latest quarter (max ano_mes_ref) globally across all institutions.
     * Used as default when anoMes parameter is not provided.
     */
    private fun getLatestQuarter(): String? {
        val sql = """
            SELECT MAX(ano_mes_ref) 
            FROM financial_data.bacen_institution
        """.trimIndent()
        val nativeQuery = entityManager.createNativeQuery(sql)
        val result = nativeQuery.singleResult as? String
        return result
    }

    @ReadOnly
    override fun getInstitutionDetail(id: String, anoMes: String?): InstitutionDetailResult? {
        // Resolve quarter: use provided anoMes or latest quarter globally
        val targetAnoMes = anoMes ?: getLatestQuarter() ?: return null

        val sql = """
            WITH latest_reports AS (
                SELECT DISTINCT ON (cod_inst)
                    cod_inst,
                    ano_mes as max_ano_mes
                FROM financial_data.bacen_bank_report
                WHERE cod_inst = ?
                ORDER BY cod_inst, ano_mes DESC
            ),
            year_ago_ano_mes AS (
                SELECT 
                    cod_inst,
                    max_ano_mes,
                    CASE 
                        WHEN CAST(SUBSTRING(max_ano_mes, 1, 4) AS INTEGER) > 0 
                        THEN LPAD((CAST(SUBSTRING(max_ano_mes, 1, 4) AS INTEGER) - 1)::TEXT, 4, '0') || SUBSTRING(max_ano_mes, 5, 2)
                        ELSE NULL
                    END as year_ago_ano_mes
                FROM latest_reports
            )
            SELECT
                bi.bacen_cod_inst,
                bi.nome_instituicao,
                fi.legal_name,
                fi.trade_name,
                fi.asset_id,
                fi.website,
                fi.social_network,
                fi.receita_data,
                bi.tipo_controle,
                bi.tipo_consolidacao,
                bi.tipo_consolidado_bancario,
                latest_reports.max_ano_mes as quarter,
                MAX(CASE WHEN bra.conta = '78187' THEN brd.saldo ELSE NULL END) as profit,
                MAX(CASE WHEN bra.conta = '78186' THEN brd.saldo ELSE NULL END) as equity,
                MAX(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE NULL END) as equity_year_ago,
                CASE 
                    WHEN MAX(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE NULL END) IS NOT NULL 
                    THEN (
                        (MAX(CASE WHEN bra.conta = '78186' THEN brd.saldo ELSE 0 END) - 
                         MAX(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END)) * 100.0 /
                        MAX(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END)
                    )
                    ELSE NULL
                END as yoy_equity_growth,
                MAX(CASE WHEN bra.conta = '79664' THEN brd.saldo ELSE NULL END) as basileia_index,
                MAX(br.tipo_instituicao) as tipo_instituicao,
                MAX(CASE WHEN bra.conta = '79665' THEN brd.saldo ELSE NULL END) as mobilizacao_index,
                MAX(CASE WHEN bra.conta = '78188' THEN brd.saldo ELSE NULL END) as total_assets,
                MAX(CASE WHEN bra.conta = '78189' THEN brd.saldo ELSE NULL END) as funding,
                MAX(CASE WHEN bra.conta = '78190' THEN brd.saldo ELSE NULL END) as loan_portfolio,
                MAX(CASE WHEN bra.conta = '78191' THEN brd.saldo ELSE NULL END) as rwa_reference_equity
            FROM financial_data.bacen_institution bi
                LEFT JOIN financial_data.financial_institution_bacen_institution fibi
                    ON fibi.bacen_cod_inst = bi.bacen_cod_inst
                    AND fibi.ano_mes_ref = bi.ano_mes_ref
                LEFT JOIN financial_data.financial_institution fi
                    ON fi.id = fibi.financial_institution_id
                LEFT JOIN latest_reports ON latest_reports.cod_inst = bi.bacen_cod_inst
                LEFT JOIN financial_data.bacen_bank_report br 
                    ON br.cod_inst = bi.bacen_cod_inst 
                    AND br.ano_mes = latest_reports.max_ano_mes
                LEFT JOIN financial_data.bacen_bank_report_data brd 
                    ON brd.bank_report_id = br.id
                LEFT JOIN financial_data.bacen_bank_report_account bra 
                    ON bra.id = brd.account_id
                LEFT JOIN year_ago_ano_mes ON year_ago_ano_mes.cod_inst = bi.bacen_cod_inst
                LEFT JOIN financial_data.bacen_bank_report br_year_ago
                    ON br_year_ago.cod_inst = bi.bacen_cod_inst AND br_year_ago.ano_mes = year_ago_ano_mes.year_ago_ano_mes
                LEFT JOIN financial_data.bacen_bank_report_data brd_year_ago 
                    ON brd_year_ago.bank_report_id = br_year_ago.id
                LEFT JOIN financial_data.bacen_bank_report_account bra_year_ago 
                    ON bra_year_ago.id = brd_year_ago.account_id AND bra_year_ago.conta IN ('78186')
            WHERE bi.bacen_cod_inst = ?
              AND bi.ano_mes_ref = ?
            GROUP BY 
                bi.bacen_cod_inst,
                bi.nome_instituicao,
                fi.legal_name,
                fi.trade_name,
                fi.asset_id,
                fi.website,
                fi.social_network,
                fi.receita_data,
                bi.tipo_controle,
                bi.tipo_consolidacao,
                bi.tipo_consolidado_bancario,
                latest_reports.max_ano_mes
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter(1, id)
        query.setParameter(2, id)
        query.setParameter(3, targetAnoMes)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as? List<Array<Any?>>

        if (results.isNullOrEmpty()) {
            return null
        }

        val row = results[0]
        val columnIndices = mapOf(
            "bacen_cod_inst" to 0,
            "nome_instituicao" to 1,
            "legal_name" to 2,
            "trade_name" to 3,
            "asset_id" to 4,
            "website" to 5,
            "social_network" to 6,
            "receita_data" to 7,
            "tipo_controle" to 8,
            "tipo_consolidacao" to 9,
            "tipo_consolidado_bancario" to 10,
            "quarter" to 11,
            "profit" to 12,
            "equity" to 13,
            "equity_year_ago" to 14,
            "yoy_equity_growth" to 15,
            "basileia_index" to 16,
            "tipo_instituicao" to 17,
            "mobilizacao_index" to 18,
            "total_assets" to 19,
            "funding" to 20,
            "loan_portfolio" to 21,
            "rwa_reference_equity" to 22,
        )

        val mapper = SqlResultMapper(row, columnIndices)

        // Parse social_network JSON
        val socialNetworksJson = mapper.getValue("social_network") as? String
        val socialNetworks = parseSocialNetworks(socialNetworksJson)

        // Parse receita_data JSON
        val receitaDataJson = mapper.getValue("receita_data") as? String
        val (mainActivity, address, founded) = parseReceitaData(receitaDataJson)

        return InstitutionDetailResult(
            bacenCodInst = mapper.getString("bacen_cod_inst") ?: id,
            nomeInstituicao = mapper.getString("nome_instituicao"),
            legalName = mapper.getString("legal_name"),
            tradeName = mapper.getString("trade_name"),
            assetId = mapper.getUUID("asset_id"),
            profit = mapper.getNumber("profit"),
            equity = mapper.getNumber("equity"),
            yoyEquityGrowth = mapper.getNumber("yoy_equity_growth"),
            basileiaIndex = mapper.getNumber("basileia_index"),
            bacenInstitutionType = mapper.getInt("tipo_instituicao"),
            tipoConsolidadoBancario = mapper.getString("tipo_consolidado_bancario"),
            institutionType = mapper.getString("tipo_consolidacao"),
            quarter = mapper.getString("quarter"),
            website = mapper.getString("website"),
            socialNetworks = socialNetworks,
            mainActivity = mainActivity,
            address = address,
            founded = founded,
            tipoControle = mapper.getString("tipo_controle"),
            tipoConsolidadoBancarioCode = mapper.getString("tipo_consolidado_bancario"),
            mobilizacaoIndex = mapper.getNumber("mobilizacao_index"),
            totalAssets = mapper.getNumber("total_assets"),
            funding = mapper.getNumber("funding"),
            loanPortfolio = mapper.getNumber("loan_portfolio"),
            rwaReferenceEquity = mapper.getNumber("rwa_reference_equity"),
        )
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
                brc.nome_relatorio as name,
                bra.grupo,
                bra.conta as account_number,
                bra.nome_coluna as column_name
            FROM financial_data.bacen_bank_report br
                INNER JOIN financial_data.bacen_bank_report_catalog brc
                    ON brc.numero_relatorio = br.numero_relatorio
                INNER JOIN financial_data.bacen_bank_report_data brd
                    ON brd.bank_report_id = br.id
                INNER JOIN financial_data.bacen_bank_report_account bra
                    ON bra.id = brd.account_id
            WHERE br.cod_inst = ?
                AND br.ano_mes IN ($quartersPlaceholder)
            ORDER BY br.ano_mes DESC, br.numero_relatorio, bra.grupo, bra.conta
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
            "grupo" to 3,
            "account_number" to 4,
            "column_name" to 5,
        )

        return results.map { row ->
            val mapper = SqlResultMapper(row, columnIndices)
            ReportDescriptorResult(
                quarter = mapper.getString("quarter") ?: "",
                reportId = mapper.getString("report_id") ?: "",
                name = mapper.getString("name"),
                group = mapper.getString("grupo"),
                accountNumber = mapper.getString("account_number"),
                columnName = mapper.getString("column_name"),
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
