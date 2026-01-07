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
import java.math.BigDecimal

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
    ): List<BacenInstitutionEntity> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(BacenInstitutionEntity::class.java)
        val root = criteriaQuery.from(BacenInstitutionEntity::class.java)

        // Join with financial_institution to fetch relationship
        root.fetch<BacenInstitutionEntity, FinancialInstitutionEntity>(
            "financialInstitution",
            jakarta.persistence.criteria.JoinType.LEFT,
        )

        // Build search filter
        buildSearchFilterPredicate(root, criteriaBuilder, query)?.let {
            criteriaQuery.where(it)
        }

        // Order by nome_instituicao for relevance
        val nomePath = root.get<String?>("nomeInstituicao")
        criteriaQuery.orderBy(
            criteriaBuilder.asc(
                criteriaBuilder.coalesce(nomePath, criteriaBuilder.literal("")),
            ),
        )

        val typedQuery: TypedQuery<BacenInstitutionEntity> = entityManager.createQuery(criteriaQuery)
        typedQuery.maxResults = limit

        return typedQuery.resultList
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
            // Parameters for fi_data CTE: trade_name, legal_name, parent_cnpj
            nativeQuery.setParameter(paramIndex++, likePattern)
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
                logoStorageKey = mapper.getString("logo_storage_key"),
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
            "nome_instituicao" to 1,
            "cnpj_instituicao_lider" to 2,
            "legal_name" to 3,
            "trade_name" to 4,
            "logo_storage_key" to 5,
            "tipo_consolidacao" to 6,
            "max_ano_mes" to 7,
            "year_ago_ano_mes" to 8,
            "similarity_score" to 9,
            "profit" to 10,
            "equity" to 11,
            "equity_year_ago" to 12,
            "yoy_equity_growth" to 13,
            "basileia_index" to 14,
            "tipo_instituicao" to 15,
            "cod_conglomerado_prudencial" to 16,
            "cod_conglomerado_financeiro" to 17,
            "tipo_consolidado_bancario" to 18,
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
                "ORDER BY fi.similarity_score DESC, basileia_index ASC, fi.bacen_cod_inst ASC"
            } else {
                "ORDER BY fi.bacen_cod_inst ASC"
            }

        // Build the query filter conditions for CTEs
        val bacenFiWhereClause =
            if (hasQuery) {
                """
                and (COALESCE(bi.nome_instituicao, '') ILIKE ?
                     OR (bi.bacen_cod_inst ILIKE ? AND bi.bacen_cod_inst NOT ILIKE 'c%'))
                """.trimIndent()
            } else {
                ""
            }

        val fiDataWhereClause =
            if (hasQuery) {
                """
                AND (
                    COALESCE(fi.trade_name, '') ILIKE ?
                    or COALESCE(fi.legal_name, '') ILIKE ?
                    or COALESCE(fi.parent_cnpj, '') ILIKE ?
                )
                """.trimIndent()
            } else {
                ""
            }

        val similarityFunction =
            if (hasQuery) {
                """
                financial_data.similarity(
                    (COALESCE(bacen_fi_data.nome_instituicao, '') || ' ' || 
                     COALESCE(fi_data.legal_name, '') || ' ' || 
                     COALESCE(fi_data.trade_name, ''))::TEXT, ?::TEXT
                ) as similarity_score
                """.trimIndent()
            } else {
                "NULL as similarity_score"
            }

        return """
            WITH latest_reports AS (
                SELECT cod_inst, MAX(ano_mes) as max_ano_mes
                FROM financial_data.bacen_bank_report
                GROUP BY cod_inst
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
            bacen_fi_data as (
                SELECT
                    bi.bacen_cod_inst,
                    bi.nome_instituicao
                from financial_data.bacen_institution bi
                where 1=1
                $bacenFiWhereClause
            ),
            fi_data as (
                select
                    fi.id as financial_institution_id,
                    fi.legal_name,
                    fi.trade_name,
                    fi.parent_cnpj,
                    fi.logo_storage_key
                FROM financial_data.financial_institution fi
                WHERE 1=1
                $fiDataWhereClause
            ),
            fi_matched_data as (
                SELECT 
                    COALESCE(bacen_fi_data.bacen_cod_inst, fibi.bacen_cod_inst) as bacen_cod_inst,
                    coalesce(bacen_fi_data.nome_instituicao, fi_data.legal_name, fi_data.trade_name) as nome_instituicao,
                    fi_data.parent_cnpj,
                    COALESCE(fi_data.financial_institution_id, fibi.financial_institution_id) as financial_institution_id,
                    fi_data.logo_storage_key,
                    fi_data.legal_name,
                    fi_data.trade_name,
                    $similarityFunction
                FROM bacen_fi_data
                    FULL OUTER JOIN financial_data.financial_institution_bacen_institution fibi
                        ON fibi.bacen_cod_inst = bacen_fi_data.bacen_cod_inst
                    FULL OUTER JOIN fi_data
                        ON fi_data.financial_institution_id = fibi.financial_institution_id
                WHERE 1=1
                  and (bacen_fi_data.bacen_cod_inst IS NOT NULL OR fi_data.financial_institution_id IS NOT NULL)
            )    
            select 
                fi.bacen_cod_inst,
                fi.nome_instituicao,
                bi.cnpj_instituicao_lider,
                fi.legal_name,
                fi.trade_name,
                fi.logo_storage_key,
                bi.tipo_consolidacao,
                latest_reports.max_ano_mes,
                year_ago_ano_mes.year_ago_ano_mes,
                fi.similarity_score,
                MAX(CASE 
                    WHEN bra.conta = '78187' THEN brd.saldo     
                    ELSE NULL 
                end) as profit,
                MAX(CASE 
                    WHEN bra.conta = '78186' THEN brd.saldo     
                    ELSE NULL 
                end) as equity,
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
                MAX(CASE 
                    WHEN bra.conta = '79664' THEN brd.saldo     
                    ELSE NULL 
                end) as basileia_index,
                MAX(br.tipo_instituicao) as tipo_instituicao,
                bi.cod_conglomerado_prudencial,
                bi.cod_conglomerado_financeiro,
                bi.tipo_consolidado_bancario
            from fi_matched_data fi   
                INNER JOIN financial_data.bacen_institution bi ON bi.bacen_cod_inst = fi.bacen_cod_inst
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
                    ON bra_year_ago.id = brd_year_ago.account_id and bra_year_ago.conta IN ('78186')
            where 1=1
            $whereClause
            GROUP BY 
                fi.bacen_cod_inst,
                fi.nome_instituicao,
                bi.cnpj_instituicao_lider,
                fi.legal_name,
                fi.trade_name,
                fi.logo_storage_key,
                bi.tipo_consolidacao,
                latest_reports.max_ano_mes,
                year_ago_ano_mes.year_ago_ano_mes,
                fi.similarity_score,
                bi.cod_conglomerado_prudencial,
                bi.cod_conglomerado_financeiro,
                bi.tipo_consolidado_bancario
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
