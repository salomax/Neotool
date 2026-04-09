package io.github.salomax.neotool.banking.domain.investment

/**
 * All asset types supported by the investment module.
 * Aligned with OFB Fase 4 APIs, Pluggy InvestmentType/SubType and Belvo InvestmentType.
 */
enum class AssetType {
    // ── Renda Variável (B3) ───────────────────────────────────────────────────
    STOCK,               // Ações (ON, PN, UNIT)
    STOCK_RIGHTS,        // Bônus de subscrição, direitos, recibos
    BDR,                 // Brazilian Depositary Receipt
    REAL_ESTATE_FUND,    // FII — Fundo de Investimento Imobiliário
    AGRIBUSINESS_FUND,   // FIAGRO — Fundo de Investimento do Agronegócio
    ETF,                 // ETF de renda variável
    ETF_FIXED_INCOME,    // ETF de renda fixa
    ETF_INTERNATIONAL,   // ETF de ativos internacionais

    // ── Renda Fixa Bancária ───────────────────────────────────────────────────
    CDB,                 // Certificado de Depósito Bancário
    RDB,                 // Recibo de Depósito Bancário
    LCI,                 // Letra de Crédito Imobiliário (isento de IR para PF)
    LCA,                 // Letra de Crédito do Agronegócio (isento de IR para PF)
    LC,                  // Letra de Câmbio
    LF,                  // Letra Financeira
    LIG,                 // Letra Imobiliária Garantida

    // ── Renda Fixa Crédito ────────────────────────────────────────────────────
    DEBENTURES,          // Debêntures
    CRI,                 // Certificado de Recebíveis Imobiliários (isento de IR para PF)
    CRA,                 // Certificado de Recebíveis do Agronegócio (isento de IR para PF)

    // ── Tesouro Direto ────────────────────────────────────────────────────────
    TREASURY,            // Todos os títulos do Tesouro Nacional

    // ── Fundos de Investimento ────────────────────────────────────────────────
    MUTUAL_FUND,         // Fundos regulados (CVM) — categoria via anbima_category

    // ── Previdência Complementar ──────────────────────────────────────────────
    PGBL,                // Plano Gerador de Benefício Livre
    VGBL,                // Vida Gerador de Benefício Livre

    // ── Estruturado ───────────────────────────────────────────────────────────
    COE,                 // Certificado de Operações Estruturadas

    // ── Outros ───────────────────────────────────────────────────────────────
    CRYPTO,
    OPTION,
    DERIVATIVES,
    OTHER,
}

/**
 * Aggregate asset class used for GraphQL holding filters.
 * Distinct from [AssetType], which represents the concrete instrument subtype.
 */
enum class InvestmentHoldingAssetClass {
    FIXED_INCOME,
    INVESTMENT_FUND,
    VARIABLE_INCOME,
    CRYPTO,
    OPTIONS,
    ;

    fun matches(assetType: AssetType): Boolean =
        when (this) {
            FIXED_INCOME -> assetType in FIXED_INCOME_TYPES
            INVESTMENT_FUND -> assetType == AssetType.MUTUAL_FUND
            VARIABLE_INCOME -> assetType in VARIABLE_INCOME_TYPES
            CRYPTO -> assetType == AssetType.CRYPTO
            OPTIONS -> assetType == AssetType.OPTION
        }

    companion object {
        private val FIXED_INCOME_TYPES =
            setOf(
                AssetType.CDB,
                AssetType.RDB,
                AssetType.LCI,
                AssetType.LCA,
                AssetType.LC,
                AssetType.LF,
                AssetType.LIG,
                AssetType.DEBENTURES,
                AssetType.CRI,
                AssetType.CRA,
                AssetType.TREASURY,
                AssetType.ETF_FIXED_INCOME,
            )

        private val VARIABLE_INCOME_TYPES =
            setOf(
                AssetType.STOCK,
                AssetType.STOCK_RIGHTS,
                AssetType.BDR,
                AssetType.REAL_ESTATE_FUND,
                AssetType.AGRIBUSINESS_FUND,
                AssetType.ETF,
                AssetType.ETF_INTERNATIONAL,
            )
    }
}

/**
 * Direction of an investment transaction.
 * Aligned with OFB (ENTRADA/SAÍDA) and Belvo (INFLOW/OUTFLOW).
 * Amount is always positive; direction is carried here.
 */
enum class InvestmentTransactionDirection { INFLOW, OUTFLOW }

/**
 * Canonical type of an investment transaction.
 * Consolidates OFB, Pluggy and Belvo enums into a single English-language enum.
 *
 * OFB mapping notes:
 *  - OFB COMPRA / APLICACAO  → PURCHASE
 *  - OFB VENDA / RESGATE     → SALE
 *  - OFB COME_COTAS + TAX    → WITHHOLDING_TAX
 *  - OFB OUTROS              → OTHER  (+ typeAdditionalInfo)
 *  - Corporate events (STOCK_SPLIT, BONUS_SHARES, etc.) fall under OFB OUTROS —
 *    we model them explicitly to support correct average-cost adjustment.
 */
enum class InvestmentTransactionType {
    // ── Compra / Venda ────────────────────────────────────────────────────────
    PURCHASE,              // Compra / Aplicação — BUY (Pluggy) / COMPRA, APLICACAO (OFB)
    SALE,                  // Venda / Resgate — SELL (Pluggy) / VENDA, RESGATE (OFB)
    CANCELLATION,          // Cancelamento — OFB CANCELAMENTO

    // ── Renda / Proventos ─────────────────────────────────────────────────────
    DIVIDEND,              // Dividendos — OFB DIVIDENDOS
    INTEREST_ON_EQUITY,    // JCP — Juros sobre Capital Próprio (exclusivo BR)
    INTEREST_PAYMENT,      // Pagamento de juros / cupom — OFB PAGAMENTO_JUROS
    AMORTIZATION,          // Amortização — OFB AMORTIZACAO
    PRIZE,                 // Prêmio (CRI/CRA) — OFB PREMIO

    // ── Vencimento ────────────────────────────────────────────────────────────
    MATURITY,              // Vencimento do título — OFB VENCIMENTO

    // ── Eventos Corporativos ──────────────────────────────────────────────────
    // OFB maps these to OUTROS — we model them explicitly for custo médio.
    STOCK_SPLIT,           // Desdobramento
    REVERSE_SPLIT,         // Grupamento
    BONUS_SHARES,          // Bonificação em ações
    SUBSCRIPTION,          // Subscrição (direitos de subscrição)

    // ── Transferências ────────────────────────────────────────────────────────
    CUSTODY_TRANSFER,      // Transferência de custódia — OFB TRANSFERENCIA_CUSTODIA
    OWNERSHIP_TRANSFER,    // Transferência de titularidade — OFB TRANSFERENCIA_TITULARIDADE
    QUOTA_TRANSFER,        // Transferência de cotas (fundos) — OFB TRANSFERENCIA_COTAS

    // ── Aluguel ───────────────────────────────────────────────────────────────
    STOCK_LENDING,         // Aluguel de ações via BTC B3 — OFB ALUGUEIS

    // ── Impostos ──────────────────────────────────────────────────────────────
    WITHHOLDING_TAX,       // Come-cotas (fundos) / IR antecipado — OFB COME_COTAS / Pluggy TAX

    // ── Penalidades (Renda Fixa Crédito) ──────────────────────────────────────
    FINE,                  // Multa contratual — OFB MULTA
    LATE_PAYMENT_INTEREST, // Juros moratórios — OFB MORA

    // ── Fallback ──────────────────────────────────────────────────────────────
    OTHER,                 // OFB OUTROS — typeAdditionalInfo is required
}

/**
 * Indexer for post-fixed and hybrid fixed income instruments.
 * Aligned with OFB EnumBankFixedIncomesContractCalculationType and Belvo remuneration.indexer.
 */
enum class Indexer {
    CDI,
    DI,
    TR,
    IPCA,
    IGP_M,
    IGP_DI,
    INPC,
    BCP,
    TLC,
    SELIC,
    PRE_FIXADO,
    OUTROS,
}

/**
 * ANBIMA fund category. Required for correct come-cotas tax treatment.
 * RENDA_FIXA and MULTIMERCADO funds with long-term profile: come-cotas at 15%.
 * RENDA_FIXA and MULTIMERCADO funds with short-term profile: come-cotas at 20%.
 */
enum class AnbimaCategory { RENDA_FIXA, ACOES, MULTIMERCADO, CAMBIAL }

/**
 * How the investment data entered the system.
 * Mirrors TransactionSource for banking transactions.
 */
enum class InvestmentSource { MANUAL, OPEN_FINANCE, FILE_IMPORT }

/**
 * How a fund holding should derive its current valuation.
 * Null / absent for non-fund holdings.
 */
enum class HoldingPricingMode {
    MANUAL,
    MARKET_DATA,
}
