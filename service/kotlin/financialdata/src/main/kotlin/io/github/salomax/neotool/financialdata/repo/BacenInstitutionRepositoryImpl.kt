package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.github.salomax.neotool.financialdata.model.BacenInstitutionEntity
import io.github.salomax.neotool.financialdata.model.FinancialInstitutionEntity
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * Custom repository implementation for BacenInstitution search operations.
 * Uses JPA Criteria API for simple queries and native SQL for complex aggregations.
 */
@Singleton
open class BacenInstitutionRepositoryImpl(
    private val entityManager: EntityManager,
) : BacenInstitutionRepositoryCustom {
    /**
     * Builds a search filter predicate for querying institutions by name or code.
     * Excludes codes starting with 'C'.
     */
    private fun buildSearchFilterPredicate(
        root: Root<BacenInstitutionEntity>,
        criteriaBuilder: CriteriaBuilder,
        query: String?,
    ): Predicate? {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }

        if (normalizedQuery == null) {
            return null
        }

        val likePattern = "%${normalizedQuery.lowercase()}%"

        val nomeInstituicaoPath = root.get<String?>("nomeInstituicao")
        val bacenCodInstPath = root.get<String>("bacenCodInst")

        // Search on nome_instituicao (case-insensitive)
        val nomePredicate =
            criteriaBuilder.like(
                criteriaBuilder
                    .lower(
                        criteriaBuilder.coalesce(nomeInstituicaoPath, criteriaBuilder.literal("")),
                    ),
                likePattern,
            )

        // Search on bacen_cod_inst (case-insensitive, excluding codes starting with 'C')
        val codInstPredicate =
            criteriaBuilder
                .and(
                    criteriaBuilder.like(
                        criteriaBuilder.lower(bacenCodInstPath),
                        likePattern,
                    ),
                    criteriaBuilder.not(
                        criteriaBuilder.like(
                            criteriaBuilder.lower(bacenCodInstPath),
                            "c%",
                        ),
                    ),
                )

        return criteriaBuilder.or(nomePredicate, codInstPredicate)
    }

    @ReadOnly
    override fun searchBacenInstitutionsAutocomplete(
        query: String,
        limit: Int,
    ): List<String> {
        val normalizedQuery = query.trim().takeIf { it.isNotBlank() }
            ?: return emptyList()

        val likePattern = "%${normalizedQuery.lowercase()}%"

        // Build native SQL query to get nome_instituicao ordered by similarity score
        // Simplified to only query bacen_institution table for better performance
        val sql = """
            SELECT 
                bi.nome_instituicao,
                financial_data.similarity(
                    COALESCE(bi.nome_instituicao, '')::TEXT,
                    ?::TEXT
                ) as similarity_score
            FROM financial_data.bacen_institution bi
            WHERE (bi.nome_instituicao ILIKE ?
                   OR (bi.bacen_cod_inst ILIKE ? AND bi.bacen_cod_inst NOT ILIKE 'c%'))
            ORDER BY similarity_score DESC, bi.nome_instituicao ASC
            LIMIT ?
        """.trimIndent()

        val nativeQuery = entityManager.createNativeQuery(sql)
        nativeQuery.setParameter(1, normalizedQuery)
        nativeQuery.setParameter(2, likePattern)
        nativeQuery.setParameter(3, likePattern)
        nativeQuery.setParameter(4, limit)

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
        first: Int,
        after: CompositeCursor?,
    ): List<InstitutionSearchResult> {
        // Build native SQL query for complex aggregation
        val sql = buildSearchQuery(query, after)
        val nativeQuery = entityManager.createNativeQuery(sql)

        // Set parameters
        var paramIndex = 1
        if (query != null && query.isNotBlank()) {
            val likePattern = "%$query%"
            // Parameters for bacen_fi_data CTE: nome_instituicao and bacen_cod_inst
            nativeQuery.setParameter(paramIndex++, likePattern)
            nativeQuery.setParameter(paramIndex++, likePattern)
            // Parameter for similarity() function in fi_matched_data CTE (uses the query text directly, not the pattern)
            nativeQuery.setParameter(paramIndex++, query)
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
            "max_ano_mes" to 10,
            "year_ago_ano_mes" to 11,
            "profit" to 12,
            "equity" to 13,
            "equity_year_ago" to 14,
            "yoy_equity_growth" to 15,
            "basileia_index" to 16,
            "tipo_instituicao" to 17,
            "legal_name" to 18,
            "trade_name" to 19,
            "tipo_consolidado_bancario" to 20,
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
                " AND fi.bacen_cod_inst > ?"
            } else {
                ""
            }

        // Build ORDER BY clause
        val orderByClause =
            if (hasQuery) {
                "ORDER BY fi.similarity_score DESC, (fi.bacen_cod_inst = fi.cod_conglomerado_prudencial) DESC, (fi.bacen_cod_inst = fi.cod_conglomerado_financeiro) DESC"
            } else {
                "ORDER BY fi.bacen_cod_inst ASC"
            }

        // Build the query filter conditions for CTEs
        val bacenFiWhereClause =
            if (hasQuery) {
                """
                and (bi.nome_instituicao ILIKE ?
                     OR (bi.bacen_cod_inst ILIKE ? AND bi.bacen_cod_inst NOT ILIKE 'c%'))
                """.trimIndent()
            } else {
                ""
            }

        return """
            WITH latest_reports AS (
                SELECT DISTINCT ON (cod_inst)
                    cod_inst,
                    ano_mes as max_ano_mes
                FROM financial_data.bacen_bank_report
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
            ),
            bacen_fi_match_data as (
                SELECT
                    bi.bacen_cod_inst,
                    bi.nome_instituicao,
                    bi.cod_conglomerado_financeiro,
                    bi.cod_conglomerado_prudencial,
                    bi.cnpj_instituicao_lider,
                    bi.tipo_consolidacao
                FROM financial_data.bacen_institution bi
                WHERE 1=1
                $bacenFiWhereClause
            ),
            bacen_fi_data as (
                SELECT
                  b.bacen_cod_inst,
                  b.nome_instituicao,
                  b.tipo_consolidacao,
                  b.cnpj_instituicao_lider,
                  b.cod_conglomerado_prudencial,
                  b.cod_conglomerado_financeiro
                FROM bacen_fi_match_data b
                UNION
                SELECT
                  child.bacen_cod_inst,
                  child.nome_instituicao,
                  child.tipo_consolidacao,
                  child.cnpj_instituicao_lider,
                  child.cod_conglomerado_prudencial,
                  child.cod_conglomerado_financeiro
                FROM financial_data.bacen_institution child
                JOIN bacen_fi_match_data b
                  ON (child.bacen_cod_inst IN (b.cod_conglomerado_financeiro, b.cod_conglomerado_prudencial)
                    OR b.bacen_cod_inst = child.cnpj_instituicao_lider)
            ),
            fi_matched_data as (
                SELECT 
                    bacen_fi_data.bacen_cod_inst,
                    coalesce(bacen_fi_data.nome_instituicao, fi_data.legal_name, fi_data.trade_name) as nome_instituicao,
                    fi_data.parent_cnpj,
                    fi_data.id AS financial_institution_id,
                    fi_data.asset_id,
                    fi_data.legal_name,
                    fi_data.trade_name,
                    bacen_fi_data.tipo_consolidacao,
                    bacen_fi_data.cnpj_instituicao_lider,
                    bacen_fi_data.cod_conglomerado_prudencial,
                    bacen_fi_data.cod_conglomerado_financeiro,
                    financial_data.similarity(
    					coalesce(bacen_fi_data.nome_instituicao, fi_data.legal_name, fi_data.trade_name)::TEXT, 
    					?::TEXT
					) as similarity_score
                FROM bacen_fi_data
                    LEFT JOIN financial_data.financial_institution_bacen_institution fibi
                        ON fibi.bacen_cod_inst = bacen_fi_data.bacen_cod_inst
                    LEFT JOIN financial_data.financial_institution fi_data
                        ON fi_data.id = fibi.financial_institution_id
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
                latest_reports.max_ano_mes,
                year_ago_ano_mes.year_ago_ano_mes,
                MAX(CASE 
                    WHEN bra.conta = '78187' THEN brd.saldo     
                    ELSE NULL 
                END) as profit,
                MAX(CASE 
                    WHEN bra.conta = '78186' THEN brd.saldo     
                    ELSE NULL 
                END) as equity,
                MAX(CASE 
                    WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE NULL END) as equity_year_ago,
                CASE 
                    WHEN MAX(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE NULL END) IS NOT NULL 
                    THEN (
                        (MAX(CASE WHEN bra.conta = '78186' THEN brd.saldo ELSE 0 END) - 
                         MAX(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END)) * 100.0 /
                        MAX(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END)
                    )
                    ELSE NULL
                END as yoy_equity_growth,	
                MAX(CASE 
                    WHEN bra.conta = '79664' THEN brd.saldo     
                    ELSE NULL 
                END) as basileia_index,
                MAX(br.tipo_instituicao) as tipo_instituicao,
                fi.legal_name,
                fi.trade_name,
                MAX(bi.tipo_consolidado_bancario) as tipo_consolidado_bancario
            FROM fi_matched_data fi
                INNER JOIN latest_reports ON latest_reports.cod_inst = fi.bacen_cod_inst
                INNER JOIN financial_data.bacen_bank_report br 
                    ON br.cod_inst = fi.bacen_cod_inst 
                    AND br.ano_mes = latest_reports.max_ano_mes
                INNER JOIN financial_data.bacen_bank_report_data brd 
                    ON brd.bank_report_id = br.id
                INNER JOIN financial_data.bacen_bank_report_account bra 
                    ON bra.id = brd.account_id and bra.conta IN ('78187', '78186', '79664')
                LEFT JOIN year_ago_ano_mes ON year_ago_ano_mes.cod_inst = fi.bacen_cod_inst
                LEFT JOIN financial_data.bacen_bank_report br_year_ago
                    ON br_year_ago.cod_inst = fi.bacen_cod_inst AND br_year_ago.ano_mes = year_ago_ano_mes.year_ago_ano_mes
                LEFT JOIN financial_data.bacen_bank_report_data brd_year_ago 
                    ON brd_year_ago.bank_report_id = br_year_ago.id
                LEFT JOIN financial_data.bacen_bank_report_account bra_year_ago 
                    ON bra_year_ago.id = brd_year_ago.account_id AND bra_year_ago.conta IN ('78186')
                LEFT JOIN financial_data.bacen_institution bi ON bi.bacen_cod_inst = fi.bacen_cod_inst
            WHERE 1=1
            $whereClause
            GROUP BY 
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
                latest_reports.max_ano_mes,
                year_ago_ano_mes.year_ago_ano_mes,
                fi.legal_name,
                fi.trade_name
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
    override fun countBacenInstitutions(query: String?): Long {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        val root = criteriaQuery.from(BacenInstitutionEntity::class.java)

        criteriaQuery.select(criteriaBuilder.count(root))

        buildSearchFilterPredicate(root, criteriaBuilder, query)?.let {
            criteriaQuery.where(it)
        }

        return entityManager.createQuery(criteriaQuery).singleResult
    }
}
