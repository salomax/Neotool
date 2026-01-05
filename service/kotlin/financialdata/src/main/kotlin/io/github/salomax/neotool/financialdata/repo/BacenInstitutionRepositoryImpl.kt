package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.github.salomax.neotool.financialdata.model.BacenInstitutionEntity
import io.github.salomax.neotool.financialdata.model.FinancialInstitutionEntity
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
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
        val nomePredicate = criteriaBuilder.like(
            criteriaBuilder.lower(
                criteriaBuilder.coalesce(nomeInstituicaoPath, criteriaBuilder.literal("")),
            ),
            likePattern,
        )

        // Search on bacen_cod_inst (case-insensitive, excluding codes starting with 'C')
        val codInstPredicate = criteriaBuilder.and(
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
        root.fetch<BacenInstitutionEntity, FinancialInstitutionEntity>("financialInstitution", jakarta.persistence.criteria.JoinType.LEFT)

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
            val likePattern = "%${query.lowercase()}%"
            // Two parameters for the OR condition (nome_instituicao and bacen_cod_inst)
            nativeQuery.setParameter(paramIndex++, likePattern)
            nativeQuery.setParameter(paramIndex++, likePattern)
        }
        if (after != null) {
            val cursorValue = after.fieldValues["id"] as? String ?: after.id
            nativeQuery.setParameter(paramIndex, cursorValue)
        }

        // Set limit
        nativeQuery.maxResults = first

        @Suppress("UNCHECKED_CAST")
        val results = nativeQuery.resultList as List<Array<Any?>>

        return results.map { row ->
            val profit = (row[6] as? Number)?.let { BigDecimal(it.toString()) }
            val equity = (row[7] as? Number)?.let { BigDecimal(it.toString()) }
            val equityYearAgo = (row[8] as? Number)?.let { BigDecimal(it.toString()) }
            val yoyEquityGrowth = (row[9] as? Number)?.let { BigDecimal(it.toString()) }
            
            InstitutionSearchResult(
                bacenCodInst = row[0] as String,
                nomeInstituicao = row[1] as? String,
                cnpjInstituicaoLider = row[2] as? String,
                legalName = row[3] as? String,
                tradeName = row[4] as? String,
                logoStorageKey = row[5] as? String,
                profit = profit,
                equity = equity,
                equityYearAgo = equityYearAgo,
                yoyEquityGrowth = yoyEquityGrowth,
                basileiaIndex = (row[10] as? Number)?.let { BigDecimal(it.toString()) },
                bacenInstitutionType = (row[11] as? Number)?.toInt(),
                tipoConsolidacao = (row[12] as? String)?.firstOrNull(),
                codConglomeradoPrudencial = row[13] as? String,
                codConglomeradoFinanceiro = row[14] as? String,
                tipoConsolidadoBancario = row[15] as? String,
                institutionType = calculateInstitutionType(
                    tipoConsolidacao = (row[12] as? String)?.firstOrNull(),
                    bacenCodInst = row[0] as String,
                    codConglomeradoPrudencial = row[13] as? String,
                    codConglomeradoFinanceiro = row[14] as? String,
                ),
            )
        }
    }

    private fun buildSearchQuery(query: String?, after: CompositeCursor?): String {
        val whereClause = buildString {
            if (query != null && query.isNotBlank()) {
                append(
                    """
                    AND (
                        LOWER(COALESCE(bi.nome_instituicao, '')) LIKE ?
                        OR (LOWER(bi.bacen_cod_inst) LIKE ? AND LOWER(bi.bacen_cod_inst) NOT LIKE 'c%')
                    )
                    """.trimIndent(),
                )
            }
            if (after != null) {
                append(" AND bi.bacen_cod_inst > ?")
            }
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
            )
            SELECT 
                bi.bacen_cod_inst,
                bi.nome_instituicao,
                bi.cnpj_instituicao_lider,
                fi.legal_name,
                fi.trade_name,
                fi.logo_storage_key,
                SUM(CASE WHEN bra.conta = '78187' THEN brd.saldo ELSE 0 END) as profit,
                SUM(CASE WHEN bra.conta = '78186' THEN brd.saldo ELSE 0 END) as equity,
                SUM(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END) as equity_year_ago,
                CASE 
                    WHEN SUM(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END) IS NOT NULL 
                         AND SUM(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END) != 0
                    THEN (
                        (SUM(CASE WHEN bra.conta = '78186' THEN brd.saldo ELSE 0 END) - 
                         SUM(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END)) * 100.0 /
                        SUM(CASE WHEN bra_year_ago.conta = '78186' THEN brd_year_ago.saldo ELSE 0 END)
                    )
                    ELSE NULL
                END as yoy_equity_growth,
                SUM(CASE WHEN bra.conta = '79664' THEN brd.saldo ELSE 0 END) as basileia_index,
                MAX(br.tipo_instituicao) as tipo_instituicao,
                bi.tipo_consolidacao,
                bi.cod_conglomerado_prudencial,
                bi.cod_conglomerado_financeiro,
                bi.tipo_consolidado_bancario
            FROM financial_data.bacen_institution bi
            LEFT JOIN latest_reports ON latest_reports.cod_inst = bi.bacen_cod_inst
            LEFT JOIN year_ago_ano_mes ON year_ago_ano_mes.cod_inst = bi.bacen_cod_inst
            LEFT JOIN financial_data.bacen_bank_report br 
                ON br.cod_inst = bi.bacen_cod_inst 
                AND br.ano_mes = latest_reports.max_ano_mes
            LEFT JOIN financial_data.bacen_bank_report_data brd 
                ON brd.bank_report_id = br.id
            LEFT JOIN financial_data.bacen_bank_report_account bra 
                ON bra.id = brd.account_id
            LEFT JOIN financial_data.bacen_bank_report br_year_ago
                ON br_year_ago.cod_inst = bi.bacen_cod_inst
                AND br_year_ago.ano_mes = year_ago_ano_mes.year_ago_ano_mes
            LEFT JOIN financial_data.bacen_bank_report_data brd_year_ago
                ON brd_year_ago.bank_report_id = br_year_ago.id
            LEFT JOIN financial_data.bacen_bank_report_account bra_year_ago
                ON bra_year_ago.id = brd_year_ago.account_id
            LEFT JOIN financial_data.financial_institution_bacen_institution fibi 
                ON fibi.bacen_cod_inst = bi.bacen_cod_inst
            LEFT JOIN financial_data.financial_institution fi 
                ON fi.id = fibi.financial_institution_id
            WHERE 1=1
            $whereClause
            GROUP BY 
                bi.bacen_cod_inst,
                bi.nome_instituicao,
                bi.cnpj_instituicao_lider,
                fi.legal_name,
                fi.trade_name,
                fi.logo_storage_key,
                bi.tipo_consolidacao,
                bi.cod_conglomerado_prudencial,
                bi.cod_conglomerado_financeiro,
                bi.tipo_consolidado_bancario
            ORDER BY bi.bacen_cod_inst ASC
        """.trimIndent()
    }

    private fun calculateInstitutionType(
        tipoConsolidacao: Char?,
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
            tipoConsolidacao == 'I' -> {
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

