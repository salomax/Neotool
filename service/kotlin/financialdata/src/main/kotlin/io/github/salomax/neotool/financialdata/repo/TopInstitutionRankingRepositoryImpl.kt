package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.github.salomax.neotool.financialdata.domain.CanonicalAccountNames
import io.github.salomax.neotool.financialdata.domain.TopInstitutionMetric
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

/**
 * Implementation of [TopInstitutionRankingRepository] using native SQL.
 * Joins bacen_institution, financial_institution, and report data to rank by one metric.
 */
@Singleton
open class TopInstitutionRankingRepositoryImpl(
    private val entityManager: EntityManager,
) : TopInstitutionRankingRepository {
    @ReadOnly
    override fun findTopByMetric(
        metric: TopInstitutionMetric,
        anoMes: String,
        limit: Int,
    ): List<TopInstitutionRankingRow> {
        val orderDirection =
            when (metric) {
                TopInstitutionMetric.MOBILIZATION_INDEX -> "ASC"
                TopInstitutionMetric.PROFIT, TopInstitutionMetric.BASILEIA_INDEX -> "DESC"
            }

        val (canonicalName, metricExpression) =
            when (metric) {
                TopInstitutionMetric.PROFIT ->
                    CanonicalAccountNames.LUCRO_LIQUIDO to "coalesce(brd.saldo_ajustado, brd.saldo)"
                TopInstitutionMetric.BASILEIA_INDEX ->
                    CanonicalAccountNames.INDICE_DE_BASILEIA to "brd.saldo"
                TopInstitutionMetric.MOBILIZATION_INDEX ->
                    CanonicalAccountNames.INDICE_DE_IMOBILIZACAO to "brd.saldo"
            }

        val sql =
            """
            SELECT
                bi.bacen_cod_inst,
                bi.nome_instituicao,
                bi.cnpj_instituicao_lider,
                fi.legal_name,
                coalesce(popular.display_name, fi.trade_name) AS trade_name,
                fi.asset_id,
                metric.metric_value,
                metric.tipo_instituicao,
                bi.tipo_consolidacao,
                bi.cod_conglomerado_prudencial,
                bi.cod_conglomerado_financeiro,
                bi.tipo_consolidado_bancario
            FROM financial_data.bacen_institution bi
            JOIN financial_data.financial_institution_bacen_institution fibi
                ON fibi.bacen_cod_inst = bi.bacen_cod_inst
                AND fibi.ano_mes_ref = ?
            JOIN financial_data.financial_institution fi
                ON fi.id = fibi.financial_institution_id
            LEFT JOIN LATERAL (
                SELECT pi.display_name
                FROM financial_data.popular_institution pi
                WHERE pi.parent_cnpj = bi.cnpj_instituicao_lider
                  AND pi.active = true
                ORDER BY pi.rank
                LIMIT 1
            ) popular ON true
            JOIN LATERAL (
                SELECT br.tipo_instituicao, $metricExpression AS metric_value
                FROM financial_data.bacen_inst_report br
                JOIN financial_data.bacen_inst_report_data brd
                    ON brd.inst_report_id = br.id
                JOIN financial_data.bacen_inst_report_account bra
                    ON bra.id = brd.account_id
                JOIN financial_data.bacen_inst_report_account_canonical brac
                    ON brac.id = bra.canonical_account_id
                    AND brac.normalized_name = ?
                WHERE br.cod_inst = bi.bacen_cod_inst
                  AND br.ano_mes = ?
            ) metric ON true
            WHERE bi.ano_mes_ref = ?
              AND bi.situacao = 'A'
              AND bi.tipo_consolidacao = 'I'
            ORDER BY metric.metric_value $orderDirection NULLS LAST
            LIMIT ?
            """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter(1, anoMes)
        query.setParameter(2, canonicalName)
        query.setParameter(3, anoMes)
        query.setParameter(4, anoMes)
        query.setParameter(5, limit)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as List<Array<Any?>>

        val columnIndices =
            mapOf(
                "bacen_cod_inst" to 0,
                "nome_instituicao" to 1,
                "cnpj_instituicao_lider" to 2,
                "legal_name" to 3,
                "trade_name" to 4,
                "asset_id" to 5,
                "metric_value" to 6,
                "tipo_instituicao" to 7,
                "tipo_consolidacao" to 8,
                "cod_conglomerado_prudencial" to 9,
                "cod_conglomerado_financeiro" to 10,
                "tipo_consolidado_bancario" to 11,
            )

        return results.map { row ->
            val mapper = SqlResultMapper(row, columnIndices)
            val bacenCodInst = mapper.getValue("bacen_cod_inst") as String
            val tipoConsolidacao = mapper.getChar("tipo_consolidacao")?.toString()
            TopInstitutionRankingRow(
                bacenCodInst = bacenCodInst,
                nomeInstituicao = mapper.getString("nome_instituicao"),
                cnpjInstituicaoLider = mapper.getString("cnpj_instituicao_lider"),
                legalName = mapper.getString("legal_name"),
                tradeName = mapper.getString("trade_name"),
                assetId = mapper.getUUID("asset_id"),
                metricValue = mapper.getNumber("metric_value"),
                bacenInstitutionType = mapper.getInt("tipo_instituicao"),
                tipoConsolidacao = tipoConsolidacao,
                codConglomeradoPrudencial = mapper.getString("cod_conglomerado_prudencial"),
                codConglomeradoFinanceiro = mapper.getString("cod_conglomerado_financeiro"),
                tipoConsolidadoBancario = mapper.getString("tipo_consolidado_bancario"),
                institutionType =
                    calculateInstitutionType(
                        tipoConsolidacao = tipoConsolidacao,
                        bacenCodInst = bacenCodInst,
                        codConglomeradoPrudencial = mapper.getString("cod_conglomerado_prudencial"),
                        codConglomeradoFinanceiro = mapper.getString("cod_conglomerado_financeiro"),
                    ),
            )
        }
    }

    @ReadOnly
    override fun findTopMetricsForInstitutions(
        anoMes: String,
        bacenCodInsts: Collection<String>,
        limit: Int,
    ): List<TopInstitutionMetricRankRow> {
        if (bacenCodInsts.isEmpty()) return emptyList()

        val bacenCodInstList = bacenCodInsts.toList()
        val inClause = bacenCodInstList.joinToString(",") { "?" }

        val sql =
            """
            WITH metric_rows AS (
                SELECT
                    bi.bacen_cod_inst,
                    brac.normalized_name,
                    CASE
                        WHEN brac.normalized_name = '${CanonicalAccountNames.LUCRO_LIQUIDO}'
                            THEN coalesce(brd.saldo_ajustado, brd.saldo)
                        ELSE brd.saldo
                    END AS metric_value
                FROM financial_data.bacen_institution bi
                JOIN financial_data.financial_institution_bacen_institution fibi
                    ON fibi.bacen_cod_inst = bi.bacen_cod_inst
                    AND fibi.ano_mes_ref = ?
                JOIN financial_data.bacen_inst_report br
                    ON br.cod_inst = bi.bacen_cod_inst
                    AND br.ano_mes = ?
                JOIN financial_data.bacen_inst_report_data brd
                    ON brd.inst_report_id = br.id
                JOIN financial_data.bacen_inst_report_account bra
                    ON bra.id = brd.account_id
                JOIN financial_data.bacen_inst_report_account_canonical brac
                    ON brac.id = bra.canonical_account_id
                WHERE bi.ano_mes_ref = ?
                  AND bi.situacao = 'A'
                  AND bi.tipo_consolidacao = 'I'
                  AND brac.normalized_name IN (
                      '${CanonicalAccountNames.LUCRO_LIQUIDO}',
                      '${CanonicalAccountNames.INDICE_DE_BASILEIA}',
                      '${CanonicalAccountNames.INDICE_DE_IMOBILIZACAO}'
                  )
                  AND bi.bacen_cod_inst IN ($inClause)
            ),
            ranked_metrics AS (
                SELECT
                    bacen_cod_inst,
                    normalized_name,
                    metric_value,
                ROW_NUMBER() OVER (
                    PARTITION BY normalized_name
                    ORDER BY
                        CASE
                            WHEN normalized_name = '${CanonicalAccountNames.INDICE_DE_IMOBILIZACAO}'
                                THEN metric_value
                        END ASC NULLS LAST,
                        CASE
                            WHEN normalized_name <> '${CanonicalAccountNames.INDICE_DE_IMOBILIZACAO}'
                                THEN metric_value
                        END DESC NULLS LAST
                ) AS metric_rank
                FROM metric_rows
            )
            SELECT
                bacen_cod_inst,
                normalized_name,
                metric_value,
                metric_rank
            FROM ranked_metrics
            WHERE metric_rank <= ?
            ORDER BY normalized_name, metric_rank
            """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        var paramIndex = 1
        query.setParameter(paramIndex++, anoMes)
        query.setParameter(paramIndex++, anoMes)
        query.setParameter(paramIndex++, anoMes)
        bacenCodInstList.forEach { bacenCodInst ->
            query.setParameter(paramIndex++, bacenCodInst)
        }
        query.setParameter(paramIndex++, limit)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as List<Array<Any?>>

        val columnIndices =
            mapOf(
                "bacen_cod_inst" to 0,
                "normalized_name" to 1,
                "metric_value" to 2,
                "metric_rank" to 3,
            )

        return results.mapNotNull { row ->
            val mapper = SqlResultMapper(row, columnIndices)
            val metric =
                normalizedNameToMetric(
                    mapper.getString("normalized_name") ?: return@mapNotNull null,
                ) ?: return@mapNotNull null
            TopInstitutionMetricRankRow(
                metric = metric,
                bacenCodInst = mapper.getValue("bacen_cod_inst") as String,
                rank = mapper.getInt("metric_rank") ?: 0,
                metricValue = mapper.getNumber("metric_value"),
            )
        }
    }

    private fun calculateInstitutionType(
        tipoConsolidacao: String?,
        bacenCodInst: String,
        codConglomeradoPrudencial: String?,
        codConglomeradoFinanceiro: String?,
    ): BacenInstitutionType? {
        return when {
            codConglomeradoFinanceiro != null && bacenCodInst == codConglomeradoFinanceiro ->
                BacenInstitutionType.FINANCIAL_GROUP
            codConglomeradoPrudencial != null && bacenCodInst == codConglomeradoPrudencial ->
                BacenInstitutionType.PRUDENTIAL_GROUP
            tipoConsolidacao == "I" ->
                BacenInstitutionType.INDIVIDUAL_INSTITUTION
            codConglomeradoPrudencial == null && codConglomeradoFinanceiro == null ->
                BacenInstitutionType.INDIVIDUAL_INSTITUTION
            else -> null
        }
    }

    private fun normalizedNameToMetric(normalizedName: String): TopInstitutionMetric? {
        return when (normalizedName) {
            CanonicalAccountNames.LUCRO_LIQUIDO -> TopInstitutionMetric.PROFIT
            CanonicalAccountNames.INDICE_DE_BASILEIA -> TopInstitutionMetric.BASILEIA_INDEX
            CanonicalAccountNames.INDICE_DE_IMOBILIZACAO -> TopInstitutionMetric.MOBILIZATION_INDEX
            else -> null
        }
    }
}
