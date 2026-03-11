package io.github.salomax.neotool.banking.graphql

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.banking.domain.rbac.BankingPermissions
import io.github.salomax.neotool.banking.graphql.dto.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.dto.InvestmentTransactionDTO
import io.github.salomax.neotool.banking.graphql.dto.IssuerDTO
import io.github.salomax.neotool.banking.graphql.resolver.FinancialAccountResolver
import io.github.salomax.neotool.banking.graphql.resolver.ImportResolver
import io.github.salomax.neotool.banking.graphql.resolver.InvestmentResolver
import io.github.salomax.neotool.banking.graphql.resolver.IssuerResolver
import io.github.salomax.neotool.banking.graphql.resolver.PdfImportResolver
import io.github.salomax.neotool.banking.graphql.resolver.TransactionResolver
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
    private val importResolver: ImportResolver,
    private val pdfImportResolver: PdfImportResolver,
    private val investmentResolver: InvestmentResolver,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "issuers",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ISSUER_VIEW) { _ ->
                        issuerResolver.listIssuers()
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
                "transactions",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        val belongsToUserId = env.getArgument<String?>("belongsToUserId")
                        val belongsToName = env.getArgument<String?>("belongsToName")
                        val tags = env.getArgument<List<String>?>("tags")
                        val billId = env.getArgument<String?>("billId")
                        val month = env.getArgument<Int?>("month")
                        val year = env.getArgument<Int?>("year")
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        transactionResolver.transactions(
                            financialAccountId = financialAccountId,
                            belongsToUserId = belongsToUserId,
                            belongsToName = belongsToName,
                            tags = tags,
                            billId = billId,
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
                "transactionCategories",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_CATEGORY_VIEW) { principal ->
                        transactionResolver.transactionCategories(principal)
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
                "pdfImportJob",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val jobId =
                            env.getArgument<String>("jobId")
                                ?: throw IllegalArgumentException("jobId is required")
                        pdfImportResolver.pdfImportJob(jobId, principal)
                    }
                },
            )
            .dataFetcher(
                "pdfImportJobs",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String?>("financialAccountId")
                        pdfImportResolver.pdfImportJobs(financialAccountId, principal)
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
                        val financialAccountId =
                            env.getArgument<String>("financialAccountId")
                                ?: throw IllegalArgumentException("financialAccountId is required")
                        investmentResolver.investmentHoldings(financialAccountId, principal)
                    }
                },
            )
            .dataFetcher(
                "allInvestmentHoldings",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_VIEW) { principal ->
                        investmentResolver.allInvestmentHoldings(principal)
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
                "submitPdfBillImport",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        pdfImportResolver.submitPdfBillImport(input, principal)
                    }
                },
            )
            .dataFetcher(
                "confirmPdfBillImport",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        pdfImportResolver.confirmPdfBillImport(input, principal)
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
                "recordInvestmentTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_CREATE) { principal ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        investmentResolver.recordInvestmentTransaction(input, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteInvestmentTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_INVESTMENT_DELETE) { principal ->
                        val id =
                            env.getArgument<String>("id")
                                ?: throw IllegalArgumentException("id is required")
                        investmentResolver.deleteInvestmentTransaction(id, principal)
                    }
                },
            )

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder =
        builder
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
                type.dataFetcher(
                    "asset",
                    createValidatedDataFetcher { env ->
                        val holding = env.getSource<InvestmentHoldingDTO>()
                        holding?.asset
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
}
