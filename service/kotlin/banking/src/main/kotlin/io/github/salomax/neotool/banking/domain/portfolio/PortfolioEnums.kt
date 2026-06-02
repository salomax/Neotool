package io.github.salomax.neotool.banking.domain.portfolio

enum class PortfolioDimensionType {
    PORTFOLIO,
    ASSET_GROUP,
    GRID_CLASS,
}

enum class PortfolioFlowEventType {
    DEPOSIT,
    WITHDRAWAL,
    DIVIDEND,
    INCOME_TAX,
    CORPORATE_ACTION,
}

enum class PortfolioRecalculationStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}

enum class MarketBenchmarkCode {
    CDI,
    IBOV,
    IPCA,
    IFIX,
}
