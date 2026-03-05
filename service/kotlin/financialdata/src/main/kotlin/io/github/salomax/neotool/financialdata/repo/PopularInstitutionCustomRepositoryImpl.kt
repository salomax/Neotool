package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

@Singleton
open class PopularInstitutionCustomRepositoryImpl(
    private val entityManager: EntityManager,
) : PopularInstitutionCustomRepository {
    @ReadOnly
    override fun findPopularWithResolvedInstitutions(anoMes: String): List<PopularInstitutionResolvedRow> {
        val sql =
            """
            SELECT
                pi.rank,
                pi.short_description,
                bi.bacen_cod_inst,
                bi.nome_instituicao,
                bi.cnpj_instituicao_lider,
                fi.legal_name,
                pi.display_name AS trade_name,
                fi.asset_id,
                br.tipo_instituicao,
                bi.tipo_consolidacao,
                bi.cod_conglomerado_prudencial,
                bi.cod_conglomerado_financeiro,
                bi.tipo_consolidado_bancario
            FROM financial_data.popular_institution pi
            JOIN financial_data.bacen_institution bi
                ON bi.cnpj_instituicao_lider = pi.parent_cnpj
                AND bi.ano_mes_ref = ?
                AND bi.bacen_cod_inst = bi.cod_conglomerado_prudencial
            JOIN financial_data.financial_institution_bacen_institution fibi
                ON fibi.bacen_cod_inst = bi.bacen_cod_inst
                AND fibi.ano_mes_ref = ?
            JOIN financial_data.financial_institution fi
                ON fi.id = fibi.financial_institution_id
            LEFT JOIN LATERAL (
                SELECT max(bir.tipo_instituicao) AS tipo_instituicao
                FROM financial_data.bacen_inst_report bir
                WHERE bir.cod_inst = bi.bacen_cod_inst
                  AND bir.ano_mes = ?
            ) br ON true
            WHERE pi.active = true
            ORDER BY pi.rank
            """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter(1, anoMes)
        query.setParameter(2, anoMes)
        query.setParameter(3, anoMes)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as List<Array<Any?>>

        val columnIndices =
            mapOf(
                "rank" to 0,
                "short_description" to 1,
                "bacen_cod_inst" to 2,
                "nome_instituicao" to 3,
                "cnpj_instituicao_lider" to 4,
                "legal_name" to 5,
                "trade_name" to 6,
                "asset_id" to 7,
                "tipo_instituicao" to 8,
                "tipo_consolidacao" to 9,
                "cod_conglomerado_prudencial" to 10,
                "cod_conglomerado_financeiro" to 11,
                "tipo_consolidado_bancario" to 12,
            )

        return results.map { row ->
            val mapper = SqlResultMapper(row, columnIndices)
            val bacenCodInst = mapper.getValue("bacen_cod_inst") as String
            val tipoConsolidacao = mapper.getChar("tipo_consolidacao")?.toString()
            PopularInstitutionResolvedRow(
                rank = (mapper.getInt("rank") ?: 0),
                shortDescription = mapper.getString("short_description") ?: "",
                bacenCodInst = bacenCodInst,
                nomeInstituicao = mapper.getString("nome_instituicao"),
                cnpjInstituicaoLider = mapper.getString("cnpj_instituicao_lider"),
                legalName = mapper.getString("legal_name"),
                tradeName = mapper.getString("trade_name"),
                assetId = mapper.getUUID("asset_id"),
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
}
