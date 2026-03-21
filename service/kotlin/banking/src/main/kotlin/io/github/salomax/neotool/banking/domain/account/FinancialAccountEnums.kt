package io.github.salomax.neotool.banking.domain.account

/**
 * Top-level account type (banking.financial_accounts.type).
 */
enum class FinancialAccountType {
    BANK,
    CREDIT,
    PAYMENT,
    CASH,
    INVESTMENT,
    LOAN,
    ASSET,
    BENEFIT,
}

/**
 * Account subtype (banking.financial_accounts.subtype).
 */
enum class FinancialAccountSubtype {
    CONTA_CORRENTE,
    CONTA_POUPANCA,
    CONTA_SALARIO,
    CONTA_DIGITAL,
    CONTA_CONJUNTA,
    CREDIT_CARD,
    CHARGE_CARD,
    PREPAID_CARD,
    VOUCHER_CARD,
    WALLET,
    BROKERAGE,
    FIXED_INCOME,
    PENSION,
    CRYPTO,
    PERSONAL_LOAN,
    MORTGAGE,
    AUTO_LOAN,
    PROPERTY,
    VEHICLE,
    OTHER_ASSET,
    // Benefit subtypes (government-managed, institution: Caixa Econômica Federal)
    FGTS,
    INSS,
}

/**
 * Card network (banking.financial_account_card_data.card_network).
 */
enum class CreditCardNetwork {
    VISA,
    MASTERCARD,
    AMERICAN_EXPRESS,
    DINERS_CLUB,
    HIPERCARD,
    ELO,
    BANDEIRA_PROPRIA,
    OUTRAS,
}

/**
 * Card level (banking.financial_account_card_data.card_level).
 */
enum class CreditCardLevel {
    STANDARD,
    GOLD,
    PLATINUM,
    INFINITE,
    BLACK,
}

/**
 * Open Finance connection status (banking.financial_accounts.connection_status).
 */
enum class ConnectionStatus {
    CONNECTED,
    SYNCING,
    DISCONNECTED,
    ERROR,
    CONSENT_EXPIRING,
    CONSENT_EXPIRED,
}
