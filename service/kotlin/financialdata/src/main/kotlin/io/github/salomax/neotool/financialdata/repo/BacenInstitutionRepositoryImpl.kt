package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

/**
 * Custom repository implementation for BacenInstitution search operations.
 * Uses native SQL for aggregation and pagination queries.
 */
@Singleton
open class BacenInstitutionRepositoryImpl(
    private val entityManager: EntityManager,
) : BacenInstitutionRepositoryCustom {
    @ReadOnly
    override fun searchBacenInstitutionsAutocomplete(
        query: String,
        anoMes: String?,
        limit: Int,
    ): List<String> {
        val normalizedQuery =
            query.trim().takeIf { it.isNotBlank() }
                ?: return emptyList()

        val targetAnoMes = anoMes ?: return emptyList()

        val likePattern = "%${normalizedQuery.lowercase()}%"

        // Build native SQL query to get nome_instituicao ordered by similarity score
        // Filter by ano_mes_ref to get institutions from specific quarter
        val sql =
            """
            SELECT 
                bi.nome_instituicao,
                financial_data.similarity(
                    COALESCE(bi.nome_instituicao, '')::TEXT,
                    ?::TEXT
                ) as similarity_score
            FROM financial_data.bacen_institution bi
            WHERE bi.ano_mes_ref = ?
              AND (bi.nome_instituicao ILIKE ?
                   OR (bi.bacen_cod_inst ILIKE ? AND bi.bacen_cod_inst NOT ILIKE 'c%'))
            ORDER BY similarity_score DESC, bi.nome_instituicao ASC
            LIMIT ?
            """.trimIndent()

        val nativeQuery = entityManager.createNativeQuery(sql)
        nativeQuery.setParameter(1, normalizedQuery)
        nativeQuery.setParameter(2, targetAnoMes)
        nativeQuery.setParameter(3, likePattern)
        nativeQuery.setParameter(4, likePattern)
        nativeQuery.setParameter(5, limit)

        @Suppress("UNCHECKED_CAST")
        val results = nativeQuery.resultList as List<Array<Any?>>

        // Extract nome_instituicao values in order, filtering out nulls
        return results
            .mapNotNull { row -> row[0] as? String }
            .filter { it.isNotBlank() }
    }

    @ReadOnly
    override fun bacenInstitutions(
        query: String?,
        anoMes: String?,
        first: Int,
        after: CompositeCursor?,
    ): List<InstitutionSearchResult> {
        val targetAnoMes = anoMes ?: return emptyList()

        // Build native SQL query for complex aggregation
        val sql = buildSearchQuery(query, after)
        val nativeQuery = entityManager.createNativeQuery(sql)

        // Set positional parameters in buildSearchQuery order:
        // 1: ano_mes_ref in params CTE
        // 2-3: query pattern filters for bacen_institutions (if hasQuery)
        // 4: query term for similarity_score in fi CTE (if hasQuery)
        // 5: cursor value for fi.bacen_cod_inst (if hasAfter)
        var paramIndex = 1
        val normalizedQuery = query?.trim()
        val hasQuery = !normalizedQuery.isNullOrBlank()
        val likePattern = if (hasQuery) "%$normalizedQuery%" else null

        // anoMes for params CTE
        nativeQuery.setParameter(paramIndex++, targetAnoMes)
        if (hasQuery) {
            nativeQuery.setParameter(paramIndex++, likePattern)
            nativeQuery.setParameter(paramIndex++, likePattern)
            nativeQuery.setParameter(paramIndex++, normalizedQuery.lowercase())
        }
        if (after != null) {
            val cursorValue = after.fieldValues["id"] as? String ?: after.id
            nativeQuery.setParameter(paramIndex, cursorValue)
        }

        // Set limit
        nativeQuery.maxResults = first

        // Get column indices by name (matches SQL SELECT order in buildSearchQuery)
        val columnIndices = getColumnIndexMap()

        // Execute query
        @Suppress("UNCHECKED_CAST")
        val results = nativeQuery.resultList as List<Array<Any?>>

        return results.map { row ->
            val mapper = SqlResultMapper(row as Array<Any?>, columnIndices)

            val profit = mapper.getNumber("profit")
            val equity = mapper.getNumber("equity")
            val equityYearAgo = mapper.getNumber("equity_year_ago")
            val yoyEquityGrowth = mapper.getNumber("yoy_equity_growth")
            val tipoConsolidacao = mapper.getChar("tipo_consolidacao")?.toString()

            InstitutionSearchResult(
                bacenCodInst = mapper.getValue("bacen_cod_inst") as String,
                nomeInstituicao = mapper.getString("nome_instituicao"),
                cnpjInstituicaoLider = mapper.getString("cnpj_instituicao_lider"),
                legalName = mapper.getString("legal_name"),
                tradeName = mapper.getString("trade_name"),
                assetId = mapper.getUUID("asset_id"),
                profit = profit,
                equity = equity,
                equityYearAgo = equityYearAgo,
                yoyEquityGrowth = yoyEquityGrowth,
                basileiaIndex = mapper.getNumber("basileia_index"),
                bacenInstitutionType = mapper.getInt("tipo_instituicao"),
                tipoConsolidacao = tipoConsolidacao,
                codConglomeradoPrudencial = mapper.getString("cod_conglomerado_prudencial"),
                codConglomeradoFinanceiro = mapper.getString("cod_conglomerado_financeiro"),
                tipoConsolidadoBancario = mapper.getString("tipo_consolidado_bancario"),
                institutionType =
                    calculateInstitutionType(
                        tipoConsolidacao = tipoConsolidacao,
                        bacenCodInst = mapper.getValue("bacen_cod_inst") as String,
                        codConglomeradoPrudencial = mapper.getString("cod_conglomerado_prudencial"),
                        codConglomeradoFinanceiro = mapper.getString("cod_conglomerado_financeiro"),
                    ),
            )
        }
    }

    /**
     * Returns a map of column names to their indices based on the SQL SELECT order.
     * This matches the exact column order in buildSearchQuery() SELECT clause.
     */
    private fun getColumnIndexMap(): Map<String, Int> {
        return mapOf(
            "bacen_cod_inst" to 0,
            "cod_conglomerado_prudencial" to 1,
            "cod_conglomerado_financeiro" to 2,
            "nome_instituicao" to 3,
            "cnpj_instituicao_lider" to 4,
            "asset_id" to 5,
            "tipo_consolidacao" to 6,
            "similarity_score" to 7,
            "financial_institution_id" to 8,
            "parent_cnpj" to 9,
            "legal_name" to 10,
            "trade_name" to 11,
            "tipo_consolidado_bancario" to 12,
            "profit" to 13,
            "equity" to 14,
            "equity_year_ago" to 15,
            "yoy_equity_growth" to 16,
            "basileia_index" to 17,
            "tipo_instituicao" to 18,
        )
    }

    private fun buildSearchQuery(
        query: String?,
        after: CompositeCursor?,
    ): String {
        val hasQuery = query != null && query.isNotBlank()
        val hasAfter = after != null

        // Build WHERE clause for cursor pagination
        val whereClause =
            if (hasAfter) {
                "AND fi.bacen_cod_inst > ?"
            } else {
                ""
            }

        // Build ORDER BY clause
        val orderByClause =
            if (hasQuery) {
                "ORDER BY fi.similarity_score DESC, " +
                    "(fi.bacen_cod_inst = fi.cod_conglomerado_prudencial) DESC, " +
                    "(fi.bacen_cod_inst = fi.cod_conglomerado_financeiro) DESC"
            } else {
                "ORDER BY fi.bacen_cod_inst ASC"
            }

        // Build the query filter conditions for CTEs
        val bacenFiWhereClause =
            if (hasQuery) {
                """
                AND (bi.nome_instituicao ILIKE ?
                     OR (bi.bacen_cod_inst ILIKE ? AND bi.bacen_cod_inst NOT ILIKE 'c%'))
                """.trimIndent()
            } else {
                ""
            }

        // Build similarity_score calculation - only when there's a query
        val similarityScoreExpression =
            if (hasQuery) {
                "financial_data.similarity(coalesce(big.nome_instituicao, fi_data.legal_name, fi_data.trade_name)" +
                    "::TEXT, ?::TEXT) as similarity_score"
            } else {
                "0.0::REAL as similarity_score"
            }

        return """
            WITH
            params AS MATERIALIZED (
                SELECT
                    source.mes::char(6) AS mes,
                    (((substring(source.mes, 1, 4)::int - 1)::text || substring(source.mes, 5, 2)))::char(6) AS mes_ago
                FROM (SELECT ?::text AS mes) source
            ),
            bacen_institutions AS (
                SELECT
                    bi.bacen_cod_inst,
                    bi.nome_instituicao,
                    bi.tipo_consolidado_bancario,
                    bi.cod_conglomerado_financeiro,
                    bi.cod_conglomerado_prudencial,
                    bi.cnpj_instituicao_lider,
                    bi.tipo_consolidacao
                FROM financial_data.bacen_institution bi
                JOIN params p ON true
                WHERE bi.ano_mes_ref = p.mes
                $bacenFiWhereClause
            ),
            bacen_institutions_and_groups AS (
                SELECT
                    b.bacen_cod_inst,
                    b.nome_instituicao,
                    b.tipo_consolidacao,
                    b.cnpj_instituicao_lider,
                    b.tipo_consolidado_bancario,
                    b.cod_conglomerado_prudencial,
                    b.cod_conglomerado_financeiro
                FROM bacen_institutions b
                UNION
                SELECT
                    child.bacen_cod_inst,
                    child.nome_instituicao,
                    child.tipo_consolidacao,
                    child.cnpj_instituicao_lider,
                    child.tipo_consolidado_bancario,
                    child.cod_conglomerado_prudencial,
                    child.cod_conglomerado_financeiro
                FROM financial_data.bacen_institution child
                JOIN params p ON true
                JOIN bacen_institutions b
                    ON (child.bacen_cod_inst IN (b.cod_conglomerado_financeiro, b.cod_conglomerado_prudencial)
                      OR b.bacen_cod_inst = child.cnpj_instituicao_lider)
                WHERE child.ano_mes_ref = p.mes
            ),
            fi AS MATERIALIZED (
                SELECT
                    big.bacen_cod_inst,
                    coalesce(big.nome_instituicao, fi_data.legal_name, fi_data.trade_name) as nome_instituicao,
                    fi_data.parent_cnpj,
                    fi_data.id AS financial_institution_id,
                    fi_data.asset_id,
                    fi_data.legal_name,
                    fi_data.trade_name,
                    big.tipo_consolidacao,
                    big.tipo_consolidado_bancario,
                    big.cnpj_instituicao_lider,
                    big.cod_conglomerado_prudencial,
                    big.cod_conglomerado_financeiro,
                    $similarityScoreExpression
                FROM bacen_institutions_and_groups big
                JOIN params p ON true
                LEFT JOIN financial_data.financial_institution_bacen_institution fibi
                    ON fibi.bacen_cod_inst = big.bacen_cod_inst
                    AND fibi.ano_mes_ref = p.mes
                LEFT JOIN financial_data.financial_institution fi_data
                    ON fi_data.id = fibi.financial_institution_id
            ),
            cur AS MATERIALIZED (
                SELECT
                    br.cod_inst,
                    max(br.tipo_instituicao) as tipo_instituicao,
                    max(coalesce(brd.saldo_ajustado, brd.saldo)) FILTER (WHERE brac.normalized_name = 'LUCRO_LIQUIDO') as profit,
                    max(brd.saldo) FILTER (WHERE brac.normalized_name = 'PATRIMONIO_LIQUIDO') as equity,
                    max(brd.saldo) FILTER (WHERE brac.normalized_name = 'INDICE_DE_BASILEIA') as basileia_index
                FROM params p
                JOIN fi ON true
                JOIN financial_data.bacen_inst_report br
                    ON br.cod_inst = fi.bacen_cod_inst
                    AND br.ano_mes = p.mes
                JOIN financial_data.bacen_inst_report_data brd
                    ON brd.inst_report_id = br.id
                JOIN financial_data.bacen_inst_report_account bra
                    ON bra.id = brd.account_id
                JOIN financial_data.bacen_inst_report_account_canonical brac
                    ON brac.id = bra.canonical_account_id
                    AND brac.normalized_name IN ('LUCRO_LIQUIDO', 'PATRIMONIO_LIQUIDO', 'INDICE_DE_BASILEIA')
                GROUP BY br.cod_inst
            ),
            ya AS MATERIALIZED (
                SELECT
                    br.cod_inst,
                    max(brd.saldo) as equity_year_ago
                FROM params p
                JOIN fi ON true
                JOIN financial_data.bacen_inst_report br
                    ON br.cod_inst = fi.bacen_cod_inst
                    AND br.ano_mes = p.mes_ago
                JOIN financial_data.bacen_inst_report_data brd
                    ON brd.inst_report_id = br.id
                JOIN financial_data.bacen_inst_report_account bra
                    ON bra.id = brd.account_id
                JOIN financial_data.bacen_inst_report_account_canonical brac
                    ON brac.id = bra.canonical_account_id
                    AND brac.normalized_name = 'PATRIMONIO_LIQUIDO'
                GROUP BY br.cod_inst
            )
            SELECT
                fi.bacen_cod_inst,
                fi.cod_conglomerado_prudencial,
                fi.cod_conglomerado_financeiro,
                fi.nome_instituicao,
                fi.cnpj_instituicao_lider,
                fi.asset_id,
                fi.tipo_consolidacao,
                fi.similarity_score,
                fi.financial_institution_id,
                fi.parent_cnpj,
                fi.legal_name,
                fi.trade_name,
                fi.tipo_consolidado_bancario,
                cur.profit,
                cur.equity,
                ya.equity_year_ago,
                CASE
                    WHEN ya.equity_year_ago IS NOT NULL
                    THEN (cur.equity - ya.equity_year_ago) * 100.0 / ya.equity_year_ago
                END as yoy_equity_growth,
                cur.basileia_index,
                cur.tipo_instituicao
            FROM fi
            LEFT JOIN cur ON cur.cod_inst = fi.bacen_cod_inst
            LEFT JOIN ya ON ya.cod_inst = fi.bacen_cod_inst
            WHERE 1=1
            $whereClause
            $orderByClause
            """.trimIndent()
    }

    private fun calculateInstitutionType(
        tipoConsolidacao: String?,
        bacenCodInst: String,
        codConglomeradoPrudencial: String?,
        codConglomeradoFinanceiro: String?,
    ): BacenInstitutionType? {
        // Priority: FINANCIAL_GROUP > PRUDENTIAL_GROUP > INDIVIDUAL_INSTITUTION
        return when {
            codConglomeradoFinanceiro != null && bacenCodInst == codConglomeradoFinanceiro -> {
                BacenInstitutionType.FINANCIAL_GROUP
            }
            codConglomeradoPrudencial != null && bacenCodInst == codConglomeradoPrudencial -> {
                BacenInstitutionType.PRUDENTIAL_GROUP
            }
            tipoConsolidacao == "I" -> {
                // Individual institution if tipo_consolidacao is 'I' and not a group leader
                BacenInstitutionType.INDIVIDUAL_INSTITUTION
            }
            // If no group codes and tipo_consolidacao is not 'I', default to individual
            // This handles cases where tipo_consolidacao is null or other values
            codConglomeradoPrudencial == null && codConglomeradoFinanceiro == null -> {
                BacenInstitutionType.INDIVIDUAL_INSTITUTION
            }
            else -> null
        }
    }

    @ReadOnly
    override fun countBacenInstitutions(
        query: String?,
        anoMes: String?,
    ): Long {
        val targetAnoMes = anoMes ?: return 0

        // Use native SQL for consistency with search query and to handle quarter filtering
        val hasQuery = query != null && query.isNotBlank()
        val likePattern = if (hasQuery) "%${query!!.lowercase()}%" else ""

        val queryFilter =
            if (hasQuery) {
                "            AND (bi.nome_instituicao ILIKE ?\n" +
                    "                   OR (bi.bacen_cod_inst ILIKE ? AND bi.bacen_cod_inst NOT ILIKE 'c%'))"
            } else {
                ""
            }

        val sql =
            """
            SELECT COUNT(DISTINCT bi.bacen_cod_inst)
            FROM financial_data.bacen_institution bi
            WHERE bi.ano_mes_ref = ?
            $queryFilter
            """.trimIndent()

        val nativeQuery = entityManager.createNativeQuery(sql)
        nativeQuery.setParameter(1, targetAnoMes)
        if (hasQuery) {
            nativeQuery.setParameter(2, likePattern)
            nativeQuery.setParameter(3, likePattern)
        }

        val result = nativeQuery.singleResult
        return when (result) {
            is Number -> result.toLong()
            else -> 0L
        }
    }
}
