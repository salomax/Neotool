package io.github.salomax.neotool.banking.graphql.config

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.banking.domain.issuer.IssuerCategory
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.shared.BankingPermissions
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentTransactionDTO
import io.github.salomax.neotool.banking.graphql.investment.GridClassTwrPeriod
import io.github.salomax.neotool.banking.graphql.issuer.IssuerDTO
import io.github.salomax.neotool.banking.graphql.merchant.MerchantDTO
import io.github.salomax.neotool.banking.graphql.preferences.BankingUserPreferencesResolver
import io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO
import io.github.salomax.neotool.banking.graphql.attachment.AttachmentResolver
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountResolver
import io.github.salomax.neotool.banking.graphql.import.ImportResolver
import io.github.salomax.neotool.banking.graphql.investment.InvestmentResolver
import io.github.salomax.neotool.banking.graphql.pendingaction.PendingActionResolver
import io.github.salomax.neotool.banking.graphql.pendingaction.AccountPendingActionDTO
import io.github.salomax.neotool.banking.graphql.issuer.IssuerResolver
import io.github.salomax.neotool.banking.graphql.billimport.BillImportResolver
import io.github.salomax.neotool.banking.graphql.merchant.MerchantResolver
import io.github.salomax.neotool.banking.graphql.product.ProductResolver
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryResolver
import io.github.salomax.neotool.banking.graphql.transaction.TransactionResolver
import io.github.salomax.neotool.banking.service.investment.InvestmentQueryService
import io.github.salomax.neotool.banking.graphql.preferences.AccountPreferencesResolver
import io.github.salomax.neotool.common.graphql.AuthenticatedGraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import jakarta.inject.Singleton

/**
 * GraphQL wiring factory for the banking subgraph.
 */
@Singleton
class BankingWiringFactory(
    private val issuerResolver: IssuerResolver,
    private val financialAccountResolver: FinancialAccountResolver,
    private val transactionResolver: TransactionResolver,
    private val transactionCategoryResolver: TransactionCategoryResolver,
    private val merchantResolver: MerchantResolver,
    private val accountPreferencesResolver: AccountPreferencesResolver,
    private val bankingUserPreferencesResolver: BankingUserPreferencesResolver,
    private val investmentResolver: InvestmentResolver,
    private val pendingActionResolver: PendingActionResolver,
    private val investmentQueryService: InvestmentQueryService,
    private val importResolver: ImportResolver,
    private val billImportResolver: BillImportResolver,
    private val attachmentResolver: AttachmentResolver,
    private val productResolver: ProductResolver,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "issuers",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ISSUER_VIEW) { _ ->
                        val categories = env.getArgument<List<String>?>("categories")?.map(IssuerCategory::valueOf)
                        issuerResolver.listIssuers(categories)
                    }
                },
            )
            .dataFetcher(
                "financialAccounts",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_VIEW) { principal ->
                        financialAccountResolver.listByAccount(principal)
                    }
                },
            )
            .dataFetcher(
                "accountPreferences",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_VIEW) { principal ->
                        accountPreferencesResolver.accountPreferences(principal)
                    }
                },
            )
            .dataFetcher(
                "bankingUserPreferences",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_VIEW) { principal ->
                        bankingUserPreferencesResolver.bankingUserPreferences(principal)
                    }
                },
            )
            .dataFetcher(
                "transactions",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val belongsToUserId = env.getArgument<String?>("belongsToUserId")
                        val belongsToName = env.getArgument<String?>("belongsToName")
                        val search = env.getArgument<String?>("search")
                        val tags = env.getArgument<List<String>?>("tags")
                        val billId = env.getArgument<String?>("billId")
                        val categoryId = env.getArgument<String?>("categoryId")
                        val uncategorized = env.getArgument<Boolean?>("uncategorized")
                        val creditDebitType =
                            env.getArgument<String?>("creditDebitType")?.let { CreditDebitType.valueOf(it) }
                        val types =
                            env.getArgument<List<String>?>("types")?.map { TransactionType.valueOf(it) }
                        val month = env.getArgument<Int?>("month")
                        val year = env.getArgument<Int?>("year")
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        transactionResolver.transactions(
                            financialAccountId = financialAccountId,
                            belongsToUserId = belongsToUserId,
                            belongsToName = belongsToName,
                            search = search,
                            tags = tags,
                            billId = billId,
                            categoryId = categoryId,
                            uncategorized = uncategorized,
                            creditDebitType = creditDebitType,
                            types = types,
                            month = month,
                            year = year,
                            first = first,
                            after = after,
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "transactionBelongsToOptions",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        transactionResolver.transactionBelongsToOptions(principal)
                    }
                },
            )
            .dataFetcher(
                "transactionTags",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        transactionResolver.transactionTags(principal)
                    }
                },
            )
            .dataFetcher(
                "transaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        transactionResolver.transaction(id, principal)
                    }
                },
            )
            .dataFetcher(
                "merchantLogoPreview",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val domain =
                            env.getArgument<String>("domain")
                                ?: throw IllegalArgumentException("domain is required")
                        merchantResolver.merchantLogoPreview(domain, principal)
                    }
                },
            )
            .dataFetcher(
                "monthSummary",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val year = env.getArgument<Int?>("year")
                        val month = env.getArgument<Int?>("month")
                        transactionResolver.monthSummary(financialAccountId, year, month, principal)
                    }
                },
            )
            .dataFetcher(
                "merchants",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val search = env.getArgument<String?>("search")
                        val after = env.getArgument<String?>("after")
                        val first = env.getArgument<Int?>("first")
                        merchantResolver.merchants(search, after, first, principal)
                    }
                },
            )
            .dataFetcher(
                "topMerchants",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val year = env.getArgument<Int?>("year")
                        val month = env.getArgument<Int?>("month")
                        val limit = env.getArgument<Int?>("limit")
                        merchantResolver.topMerchants(financialAccountId, year, month, limit, principal)
                    }
                },
            )
            .dataFetcher(
                "transactionCategories",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_VIEW) { principal ->
                        transactionResolver.transactionCategories(principal)
                    }
                },
            )
            .dataFetcher(
                "transactionRules",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_VIEW) { principal ->
                        val isSystem = env.getArgument<Boolean?>("isSystem")
                        val search = env.getArgument<String?>("search")
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        transactionCategoryResolver.transactionRules(isSystem, search, first, after, principal)
                    }
                },
            )
            .dataFetcher(
                "transactionRulesByIds",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_VIEW) { principal ->
                        val ids = env.getArgument<List<String>>("ids")
                            ?: throw IllegalArgumentException("ids is required")
                        transactionCategoryResolver.transactionRulesByIds(ids, principal)
                    }
                },
            )
            .dataFetcher(
                "transactionRuleHistoricalMatchCount",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_VIEW) { principal ->
                        val id = env.getArgument<String>("id")
                            ?: throw IllegalArgumentException("id is required")
                        transactionCategoryResolver.transactionRuleHistoricalMatchCount(id, principal)
                    }
                },
            )
            .dataFetcher(
                "previewTransactionRule",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_VIEW) { _ ->
                        val pattern = env.getArgument<String>("pattern")
                            ?: throw IllegalArgumentException("pattern is required")
                        val patternType = env.getArgument<String>("patternType")
                            ?: throw IllegalArgumentException("patternType is required")
                        val sample = env.getArgument<String>("sample")
                            ?: throw IllegalArgumentException("sample is required")
                        transactionCategoryResolver.previewTransactionRule(pattern, patternType, sample)
                    }
                },
            )
            .dataFetcher(
                "similarTransactionEditPreview",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        transactionResolver.similarTransactionEditPreview(id, principal)
                    }
                },
            )
            .dataFetcher(
                "transactionsByCategory",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        transactionResolver.transactionsByCategory(
                            financialAccountId = env.getArgument("financialAccountId"),
                            belongsToUserId = env.getArgument("belongsToUserId"),
                            belongsToName = env.getArgument("belongsToName"),
                            search = env.getArgument("search"),
                            tags = env.getArgument("tags"),
                            billId = env.getArgument("billId"),
                            categoryId = env.getArgument("categoryId"),
                            uncategorized = env.getArgument("uncategorized"),
                            year = env.getArgument("year"),
                            month = env.getArgument("month"),
                            applicableTo = env.getArgument("applicableTo"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "cashFlowSeries",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        transactionResolver.cashFlowSeries(
                            financialAccountId = env.getArgument("financialAccountId"),
                            startYear = env.getArgument<Int>("startYear")
                                ?: throw IllegalArgumentException("startYear is required"),
                            startMonth = env.getArgument<Int>("startMonth")
                                ?: throw IllegalArgumentException("startMonth is required"),
                            endYear = env.getArgument<Int>("endYear")
                                ?: throw IllegalArgumentException("endYear is required"),
                            endMonth = env.getArgument<Int>("endMonth")
                                ?: throw IllegalArgumentException("endMonth is required"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "dashboardPeriodSummary",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        transactionResolver.dashboardPeriodSummary(
                            financialAccountId = env.getArgument("financialAccountId"),
                            startYear = env.getArgument<Int>("startYear")
                                ?: throw IllegalArgumentException("startYear is required"),
                            startMonth = env.getArgument<Int>("startMonth")
                                ?: throw IllegalArgumentException("startMonth is required"),
                            endYear = env.getArgument<Int>("endYear")
                                ?: throw IllegalArgumentException("endYear is required"),
                            endMonth = env.getArgument<Int>("endMonth")
                                ?: throw IllegalArgumentException("endMonth is required"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "categoryBreakdownForRange",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        transactionResolver.categoryBreakdownForRange(
                            financialAccountId = env.getArgument("financialAccountId"),
                            startYear = env.getArgument<Int>("startYear")
                                ?: throw IllegalArgumentException("startYear is required"),
                            startMonth = env.getArgument<Int>("startMonth")
                                ?: throw IllegalArgumentException("startMonth is required"),
                            endYear = env.getArgument<Int>("endYear")
                                ?: throw IllegalArgumentException("endYear is required"),
                            endMonth = env.getArgument<Int>("endMonth")
                                ?: throw IllegalArgumentException("endMonth is required"),
                            applicableTo = env.getArgument("applicableTo"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "portfolioNetWorthSeries",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        transactionResolver.portfolioNetWorthSeries(
                            financialAccountId = env.getArgument("financialAccountId"),
                            startYear = env.getArgument<Int>("startYear")
                                ?: throw IllegalArgumentException("startYear is required"),
                            startMonth = env.getArgument<Int>("startMonth")
                                ?: throw IllegalArgumentException("startMonth is required"),
                            endYear = env.getArgument<Int>("endYear")
                                ?: throw IllegalArgumentException("endYear is required"),
                            endMonth = env.getArgument<Int>("endMonth")
                                ?: throw IllegalArgumentException("endMonth is required"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "transactionsByTag",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        transactionResolver.transactionsByTag(
                            financialAccountId = env.getArgument("financialAccountId"),
                            belongsToUserId = env.getArgument("belongsToUserId"),
                            belongsToName = env.getArgument("belongsToName"),
                            search = env.getArgument("search"),
                            tags = env.getArgument("tags"),
                            billId = env.getArgument("billId"),
                            categoryId = env.getArgument("categoryId"),
                            uncategorized = env.getArgument("uncategorized"),
                            year = env.getArgument("year"),
                            month = env.getArgument("month"),
                            applicableTo = env.getArgument("applicableTo"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "transactionImports",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        importResolver.transactionImports(financialAccountId, principal)
                    }
                },
            )
            .dataFetcher(
                "transactionImport",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        importResolver.transactionImport(id, principal)
                    }
                },
            )
            .dataFetcher(
                "pendingReviewImports",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        importResolver.pendingReviewImports(financialAccountId, principal)
                    }
                },
            )
            .dataFetcher(
                "billImportJob",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val jobId =
                            env.getArgument<String>("jobId")
                                ?: throw IllegalArgumentException("jobId is required")
                        billImportResolver.billImportJob(jobId, principal)
                    }
                },
            )
            .dataFetcher(
                "billImportJobs",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        billImportResolver.billImportJobs(financialAccountId, principal)
                    }
                },
            )
            .dataFetcher(
                "creditCardBills",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId =
                            env.getArgument<String>("financialAccountId")
                                ?: throw IllegalArgumentException("financialAccountId is required")
                        transactionResolver.creditCardBills(financialAccountId, principal)
                    }
                },
            )
            .dataFetcher(
                "creditCardBill",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId =
                            env.getArgument<String>("financialAccountId")
                                ?: throw IllegalArgumentException("financialAccountId is required")
                        val closingDate = env.getArgument<String?>("closingDate")
                        transactionResolver.creditCardBill(financialAccountId, closingDate, principal)
                    }
                },
            )
            .dataFetcher(
                "investmentHoldings",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val assetClass = env.getArgument<String?>("assetClass")
                        investmentResolver.investmentHoldings(financialAccountId, assetClass, principal)
                    }
                },
            )
            .dataFetcher(
                "investmentCashBalance",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        investmentResolver.investmentCashBalance(financialAccountId, principal)
                    }
                },
            )
            .dataFetcher(
                "investmentPortfolioSummary",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val period = env.getArgument<String?>("period")
                        investmentResolver.investmentPortfolioSummary(financialAccountId, period, principal)
                    }
                },
            )
            .dataFetcher(
                "portfolioTwrDebug",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val period = env.getArgument<String?>("period")
                        investmentResolver.portfolioTwrDebug(financialAccountId, period, principal)
                    }
                },
            )
            .dataFetcher(
                "investmentCashTransactions",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        investmentResolver.investmentCashTransactions(
                            financialAccountId,
                            first,
                            after,
                            principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "investmentHolding",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        investmentResolver.investmentHolding(id, principal)
                    }
                },
            )
            .dataFetcher(
                "investmentTransactions",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val holdingId =
                            env.getArgument<String>("holdingId")
                                ?: throw IllegalArgumentException("holdingId is required")
                        investmentResolver.investmentTransactions(holdingId, principal)
                    }
                },
            )
            .dataFetcher(
                "investmentTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        investmentResolver.investmentTransaction(id, principal)
                    }
                },
            )
            .dataFetcher(
                "investmentAsset",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { _ ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        investmentResolver.investmentAsset(id)
                    }
                },
            )
            .dataFetcher(
                "holdingPerformance",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        fun parseLocalDateArgument(name: String): java.time.LocalDate? =
                            when (val raw = env.getArgument<Any?>(name)) {
                                null -> null
                                is java.time.LocalDate -> raw
                                is String -> java.time.LocalDate.parse(raw)
                                else -> error("$name must be a LocalDate or ISO-8601 string")
                            }

                        val holdingId =
                            env.getArgument<String>("holdingId")
                                ?: throw IllegalArgumentException("holdingId is required")
                        val resolution = env.getArgument<String?>("resolution")
                        val fromDate = parseLocalDateArgument("fromDate")
                        val toDate = parseLocalDateArgument("toDate")
                        val includeProjection = env.getArgument<Boolean?>("includeProjection")
                        investmentResolver.holdingPerformance(holdingId, resolution, fromDate, toDate, includeProjection, principal)
                    }
                },
            )
            .dataFetcher(
                "portfolioPerformance",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val groupKey = env.getArgument<String?>("groupKey")
                        val subgroupKey = env.getArgument<String?>("subgroupKey")
                        val assetTypes = env.getArgument<List<String>?>("assetTypes")
                        val resolution = env.getArgument<String?>("resolution")
                        val includeProjection = env.getArgument<Boolean?>("includeProjection")
                        investmentResolver.portfolioPerformance(
                            financialAccountId,
                            groupKey,
                            subgroupKey,
                            assetTypes,
                            resolution,
                            includeProjection,
                            principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "gridClassTwrSeries",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val gridClassKey =
                            env.getArgument<String>("gridClassKey")
                                ?: throw IllegalArgumentException("gridClassKey is required")
                        val period = env.getArgument<String?>("period")
                        investmentResolver.gridClassTwrSeries(
                            financialAccountId = financialAccountId,
                            gridClassKey = gridClassKey,
                            period = period?.let(GridClassTwrPeriod::valueOf),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "investmentHoldingPerformance",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val holdingId =
                            env.getArgument<String>("holdingId")
                                ?: throw IllegalArgumentException("holdingId is required")
                        investmentResolver.investmentHoldingPerformance(holdingId, principal)
                    }
                },
            )
            .dataFetcher(
                "pendingActions",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        val status = env.getArgument<String?>("status")
                        pendingActionResolver.pendingActions(status, principal)
                    }
                },
            )
            .dataFetcher(
                "products",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        productResolver.products(
                            search = env.getArgument("search"),
                            category = env.getArgument("category"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "product",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val id = env.getArgument<String>("id")
                            ?: throw IllegalArgumentException("id is required")
                        productResolver.product(id, principal)
                    }
                },
            )
            .dataFetcher(
                "productVariants",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        productResolver.productVariants(
                            search = env.getArgument("search"),
                            productId = env.getArgument("productId"),
                            limit = env.getArgument("limit"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "purchaseItems",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val transactionId = env.getArgument<String>("transactionId")
                            ?: throw IllegalArgumentException("transactionId is required")
                        productResolver.purchaseItems(transactionId, principal)
                    }
                },
            )
            .dataFetcher(
                "nfceDocument",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val transactionId = env.getArgument<String>("transactionId")
                            ?: throw IllegalArgumentException("transactionId is required")
                        productResolver.nfceDocument(transactionId, principal)
                    }
                },
            )
            .dataFetcher(
                "unmappedItems",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        productResolver.unmappedItems(env.getArgument("limit"), principal)
                    }
                },
            )
            .dataFetcher(
                "productPriceHistory",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val productId = env.getArgument<String>("productId")
                            ?: throw IllegalArgumentException("productId is required")
                        productResolver.productPriceHistory(
                            productId = productId,
                            variantId = env.getArgument("variantId"),
                            startDate = env.getArgument("startDate"),
                            endDate = env.getArgument("endDate"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "productConsumptionTrend",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val productId = env.getArgument<String>("productId")
                            ?: throw IllegalArgumentException("productId is required")
                        productResolver.productConsumptionTrend(
                            productId = productId,
                            periodMonths = env.getArgument("periodMonths"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "productStoreComparison",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val productId = env.getArgument<String>("productId")
                            ?: throw IllegalArgumentException("productId is required")
                        productResolver.productStoreComparison(
                            productId = productId,
                            startDate = env.getArgument("startDate"),
                            endDate = env.getArgument("endDate"),
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "spendingByNcm",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        productResolver.spendingByNcm(
                            depth = env.getArgument("depth"),
                            startDate = env.getArgument("startDate"),
                            endDate = env.getArgument("endDate"),
                            principal = principal,
                        )
                    }
                },
            )

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "createFinancialAccount",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        financialAccountResolver.create(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateFinancialAccount",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_CREATE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        financialAccountResolver.update(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteFinancialAccount",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_CREATE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        financialAccountResolver.delete(id, principal)
                    }
                },
            )
            .dataFetcher(
                "updateAccountPreferences",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_VIEW) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        accountPreferencesResolver.updateAccountPreferences(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateBankingUserPreferences",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_VIEW) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        bankingUserPreferencesResolver.updateBankingUserPreferences(input, principal)
                    }
                },
            )
            .dataFetcher(
                "createTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        transactionResolver.createTransaction(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        transactionResolver.updateTransaction(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_DELETE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        transactionResolver.deleteTransaction(id, principal)
                    }
                },
            )
            .dataFetcher(
                "bulkCategorize",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        @Suppress("UNCHECKED_CAST")
                        val transactionIds =
                            (env.getArgument<Any>("transactionIds") as? List<*>)
                                ?.map { it.toString() }
                                ?: throw IllegalArgumentException("transactionIds is required")
                        val categoryId =
                            env.getArgument<String>("categoryId")
                                ?: throw IllegalArgumentException("categoryId is required")
                        transactionResolver.bulkCategorize(transactionIds, categoryId, principal)
                    }
                },
            )
            .dataFetcher(
                "applyTransactionEditToSimilar",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        val matchDescription =
                            env.getArgument<String>("matchDescription")
                                ?: throw IllegalArgumentException("matchDescription is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        val applyToFuture = env.getArgument<Boolean?>("applyToFuture") ?: true
                        val applyToExisting = env.getArgument<Boolean?>("applyToExisting") ?: true
                        transactionResolver.applyTransactionEditToSimilar(
                            id = id,
                            matchDescription = matchDescription,
                            input = input,
                            applyToFuture = applyToFuture,
                            applyToExisting = applyToExisting,
                            principal = principal,
                        )
                    }
                },
            )
            .dataFetcher(
                "importTransactionsFile",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        importResolver.importTransactionsFile(input, principal)
                    }
                },
            )
            .dataFetcher(
                "importNfce",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val financialAccountId =
                            env.getArgument<String>("financialAccountId")
                                ?: throw IllegalArgumentException("financialAccountId is required")
                        val qrCodeUrl =
                            env.getArgument<String>("qrCodeUrl")
                                ?: throw IllegalArgumentException("qrCodeUrl is required")
                        val input =
                            mapOf(
                                "financialAccountId" to financialAccountId,
                                "qrCodeUrl" to qrCodeUrl,
                            )
                        importResolver.importNfce(input, principal)
                    }
                },
            )
            .dataFetcher(
                "resolveImportDuplicate",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        importResolver.resolveImportDuplicate(input, principal)
                    }
                },
            )
            .dataFetcher(
                "cancelImportReview",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val jobId =
                            env.getArgument<String>("jobId")
                                ?: throw IllegalArgumentException("jobId is required")
                        importResolver.cancelImportReview(jobId, principal)
                    }
                },
            )
            .dataFetcher(
                "submitBillImport",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        billImportResolver.submitBillImport(input, principal)
                    }
                },
            )
            .dataFetcher(
                "confirmBillImport",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        billImportResolver.confirmBillImport(input, principal)
                    }
                },
            )
            .dataFetcher(
                "payBill",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val billId =
                            env.getArgument<String>("billId")
                                ?: throw IllegalArgumentException("billId is required")
                        val amount =
                            env.getArgument<Double>("amount")
                                ?: throw IllegalArgumentException("amount is required")
                        val sourceAccountId = env.getArgument<String?>("sourceAccountId")
                        transactionResolver.payBill(billId, amount, sourceAccountId, principal)
                    }
                },
            )
            .dataFetcher(
                "createInvestmentAsset",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.createInvestmentAsset(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateInvestmentAsset",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val id = env.getArgument<String>("id") ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.updateInvestmentAsset(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "recordInvestmentTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.recordInvestmentTransaction(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateInvestmentTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val id = env.getArgument<String>("id") ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.updateInvestmentTransaction(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "createInvestmentCashTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.createInvestmentCashTransaction(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateInvestmentCashTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val id = env.getArgument<String>("id") ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.updateInvestmentCashTransaction(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteInvestmentCashTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val id = env.getArgument<String>("id") ?: throw IllegalArgumentException("id is required")
                        investmentResolver.deleteInvestmentCashTransaction(id, principal)
                    }
                },
            )
            .dataFetcher(
                "createMerchant",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        merchantResolver.createMerchant(input, principal)
                    }
                },
            )
            .dataFetcher(
                "createTransactionCategory",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        transactionCategoryResolver.createTransactionCategory(input, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteInvestmentTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        investmentResolver.deleteInvestmentTransaction(id, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteInvestmentHolding",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        investmentResolver.deleteInvestmentHolding(id, principal)
                    }
                },
            )
            .dataFetcher(
                "triggerPortfolioRecalculation",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val recalculateFrom = env.getArgument<String?>("recalculateFrom")
                        investmentResolver.triggerPortfolioRecalculation(financialAccountId, recalculateFrom, principal)
                    }
                },
            )
            .dataFetcher(
                "updateTransactionCategory",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        transactionCategoryResolver.updateTransactionCategory(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "createProduct",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        productResolver.createProduct(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateProduct",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val id = env.getArgument<String>("id")
                            ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        productResolver.updateProduct(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "createProductVariant",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        productResolver.createProductVariant(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateProductVariant",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val id = env.getArgument<String>("id")
                            ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        productResolver.updateProductVariant(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "mapItemToVariant",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val rawDescription = env.getArgument<String>("rawDescription")
                            ?: throw IllegalArgumentException("rawDescription is required")
                        val variantId = env.getArgument<String>("variantId")
                            ?: throw IllegalArgumentException("variantId is required")
                        productResolver.mapItemToVariant(rawDescription, variantId, principal)
                    }
                },
            )
            .dataFetcher(
                "unmapItem",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val aliasId = env.getArgument<String>("aliasId")
                            ?: throw IllegalArgumentException("aliasId is required")
                        productResolver.unmapItem(aliasId, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteTransactionCategory",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        transactionCategoryResolver.deleteTransactionCategory(id, principal)
                    }
                },
            )
            .dataFetcher(
                "createTransactionRule",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        transactionCategoryResolver.createTransactionRule(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateTransactionRule",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        transactionCategoryResolver.updateTransactionRule(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteTransactionRule",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        transactionCategoryResolver.deleteTransactionRule(id, principal)
                    }
                },
            )
            .dataFetcher(
                "applyTransactionRuleToHistorical",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        transactionCategoryResolver.applyTransactionRuleToHistorical(id, principal)
                    }
                },
            )
            .dataFetcher(
                "reorderTransactionRules",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val ids = env.getArgument<List<String>>("ids")
                            ?: throw IllegalArgumentException("ids is required")
                        transactionCategoryResolver.reorderTransactionRules(ids, principal)
                    }
                },
            )
            .dataFetcher(
                "setSystemTransactionRuleEnabled",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_MANAGE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        val enabled =
                            env.getArgument<Boolean?>("enabled")
                                ?: throw IllegalArgumentException("enabled is required")
                        transactionCategoryResolver.setSystemTransactionRuleEnabled(id, enabled, principal)
                    }
                },
            )
            .dataFetcher(
                "attachFileToTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        attachmentResolver.attachFileToTransaction(input, principal)
                    }
                },
            )
            .dataFetcher(
                "removeTransactionAttachment",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val attachmentId =
                            env.getArgument<String>("attachmentId")
                                ?: throw IllegalArgumentException("attachmentId is required")
                        attachmentResolver.removeTransactionAttachment(attachmentId, principal)
                    }
                },
            )
            .dataFetcher(
                "attachFileToHolding",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        attachmentResolver.attachFileToHolding(input, principal)
                    }
                },
            )
            .dataFetcher(
                "removeHoldingAttachment",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val attachmentId =
                            env.getArgument<String>("attachmentId")
                                ?: throw IllegalArgumentException("attachmentId is required")
                        attachmentResolver.removeHoldingAttachment(attachmentId, principal)
                    }
                },
            )
            .dataFetcher(
                "confirmHoldingMaturity",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        @Suppress("UNCHECKED_CAST")
                        val command =
                            env.getArgument<Map<String, Any?>>("command")
                                ?: throw IllegalArgumentException("command is required")
                        pendingActionResolver.confirmHoldingMaturity(command, principal)
                    }
                },
            )
            .dataFetcher(
                "confirmCouponPayment",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        @Suppress("UNCHECKED_CAST")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.confirmCouponPayment(input, principal)
                    }
                },
            )
            .dataFetcher(
                "confirmWithholdingTax",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        @Suppress("UNCHECKED_CAST")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.confirmWithholdingTax(input, principal)
                    }
                },
            )
            .dataFetcher(
                "recordAmortization",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        @Suppress("UNCHECKED_CAST")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.recordAmortization(input, principal)
                    }
                },
            )
            .dataFetcher(
                "recordStockLending",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        @Suppress("UNCHECKED_CAST")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.recordStockLending(input, principal)
                    }
                },
            )
            .dataFetcher(
                "transferCustody",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        @Suppress("UNCHECKED_CAST")
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.transferCustody(input, principal)
                    }
                },
            )
            .dataFetcher(
                "dismissPendingAction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_UPDATE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        pendingActionResolver.dismissPendingAction(id, principal)
                    }
                },
            )

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder =
        builder
            .type("Merchant") { type ->
                type.dataFetcher(
                    "logoAsset",
                    createValidatedDataFetcher { env ->
                        val merchant = env.getSource<MerchantDTO>()
                        merchant?.logoAsset
                    },
                )
            }
            .type("Issuer") { type ->
                type.dataFetcher(
                    "logoAsset",
                    createValidatedDataFetcher { env ->
                        val issuer = env.getSource<IssuerDTO>()
                        issuer?.logoAsset
                    },
                )
            }
            .type("FinancialAccount") { type ->
                type.dataFetcher(
                    "issuer",
                    createValidatedDataFetcher { env ->
                        val account = env.getSource<FinancialAccountDTO>()
                        account?.issuer
                    },
                )
            }
            .type("InvestmentHolding") { type ->
                type
                    .dataFetcher(
                        "asset",
                        createValidatedDataFetcher { env ->
                            val holding = env.getSource<InvestmentHoldingDTO>()
                            holding?.asset
                        },
                    )
                    .dataFetcher(
                        "attachments",
                        createValidatedDataFetcher { env ->
                            env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                                val holding = env.getSource<InvestmentHoldingDTO>()
                                holding?.id?.let {
                                    attachmentResolver.holdingAttachments(it.toString(), principal)
                                } ?: emptyList<Any>()
                            }
                        },
                    )
            }
            .type("InvestmentTransaction") { type ->
                type.dataFetcher(
                    "brokerNote",
                    createValidatedDataFetcher { env ->
                        val transaction = env.getSource<InvestmentTransactionDTO>()
                        transaction?.brokerNote
                    },
                )
            }
            .type("AccountPendingAction") { type ->
                type.dataFetcher(
                    "relatedHolding",
                    createValidatedDataFetcher { env ->
                        env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                            val dto = env.getSource<AccountPendingActionDTO>()
                            if (dto?.entityType != "HOLDING") return@withPermission null
                            val eid = dto.entityId ?: return@withPermission null
                            val accountId =
                                principal.currentAccountId
                                    ?: throw IllegalArgumentException("No active account in token")
                            investmentQueryService.getHolding(eid, accountId)
                        }
                    },
                )
            }
            .type("Transaction") { type ->
                type.dataFetcher(
                    "attachments",
                    createValidatedDataFetcher { env ->
                        val transaction = env.getSource<TransactionDTO>()
                            ?: return@createValidatedDataFetcher emptyList<Any>()
                        // Fast path: attachments were pre-loaded in the batch list query.
                        // The parent Transaction was already permission-checked — no redundant check here.
                        val preloaded = transaction.attachments
                        if (preloaded != null) {
                            return@createValidatedDataFetcher attachmentResolver.mapAttachmentsToDTOs(preloaded)
                        }
                        // Fallback for single-transaction responses (mutations, individual fetches).
                        env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                            attachmentResolver.transactionAttachments(transaction.id.toString(), principal)
                        }
                    },
                )
                type.dataFetcher(
                    "linkedInvestmentAsset",
                    createValidatedDataFetcher { env ->
                        env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                            val transaction = env.getSource<TransactionDTO>()
                            val linkId = transaction?.investmentTransactionId ?: return@withPermission null
                            val accountId =
                                principal.currentAccountId
                                    ?: throw IllegalArgumentException("No active account in token")
                            investmentQueryService.getLinkedInvestmentAssetForCashLeg(linkId, accountId)
                        }
                    },
                )
            }
}
