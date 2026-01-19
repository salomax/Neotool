package io.github.salomax.neotool.financialdata.domain

/**
 * Canonical account names for financial metrics.
 * These normalized names are used to identify accounts consistently across different quarters,
 * even when the underlying account numbers (conta) change.
 */
object CanonicalAccountNames {
    /**
     * Lucro Líquido (Net Profit)
     * Previously mapped from account number: 78187
     */
    const val LUCRO_LIQUIDO = "LUCRO_LIQUIDO"

    /**
     * Patrimônio Líquido (Equity)
     * Previously mapped from account number: 78186
     */
    const val PATRIMONIO_LIQUIDO = "PATRIMONIO_LIQUIDO"

    /**
     * Índice de Basileia (Basileia Index)
     * Previously mapped from account number: 79664
     */
    const val INDICE_DE_BASILEIA = "INDICE_DE_BASILEIA"

    /**
     * Índice de Imobilização (Immobilization Index)
     * Previously mapped from account number: 79665
     */
    const val INDICE_DE_IMOBILIZACAO = "INDICE_DE_IMOBILIZACAO"

    /**
     * Ativo Total (Total Assets)
     * Previously mapped from account number: 78188
     */
    const val ATIVO_TOTAL = "ATIVO_TOTAL"

    /**
     * Captações (Funding)
     * Previously mapped from account number: 78189
     */
    const val CAPTACOES = "CAPTACOES"

    /**
     * Carteira de Crédito (Loan Portfolio)
     * Previously mapped from account number: 78190
     */
    const val CARTEIRA_DE_CREDITO = "CARTEIRA_DE_CREDITO"

    /**
     * Patrimônio de Referência (RWA Reference Equity)
     * Previously mapped from account number: 78191
     */
    const val PATRIMONIO_DE_REFERENCIA = "PATRIMONIO_DE_REFERENCIA"
}
