package io.github.salomax.neotool.banking.graphql

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.banking.domain.rbac.BankingPermissions
import io.github.salomax.neotool.banking.graphql.dto.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.dto.IssuerDTO
import io.github.salomax.neotool.banking.graphql.resolver.FinancialAccountResolver
import io.github.salomax.neotool.banking.graphql.resolver.ImportResolver
import io.github.salomax.neotool.banking.graphql.resolver.IssuerResolver
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
                        val month = env.getArgument<Int?>("month")
                        val year = env.getArgument<Int?>("year")
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        transactionResolver.transactions(
                            financialAccountId = financialAccountId,
                            belongsToUserId = belongsToUserId,
                            belongsToName = belongsToName,
                            tags = tags,
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
                        val id = env.getArgument<String>("id")
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
                        val id = env.getArgument<String>("id")
                            ?: throw IllegalArgumentException("id is required")
                        importResolver.transactionImport(id, principal)
                    }
                },
            )
            .dataFetcher(
                "creditCardBills",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String>("financialAccountId")
                            ?: throw IllegalArgumentException("financialAccountId is required")
                        transactionResolver.creditCardBills(financialAccountId, principal)
                    }
                },
            )
            .dataFetcher(
                "creditCardBill",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
                        val financialAccountId = env.getArgument<String>("financialAccountId")
                            ?: throw IllegalArgumentException("financialAccountId is required")
                        val closingDate = env.getArgument<String?>("closingDate")
                        transactionResolver.creditCardBill(financialAccountId, closingDate, principal)
                    }
                },
            )

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "createFinancialAccount",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ACCOUNT_CREATE) { principal ->
                        val input = env.getArgument<Map<String, Any?>>("input")
                            ?: throw IllegalArgumentException("input is required")
                        financialAccountResolver.create(input, principal)
                    }
                },
            )
            .dataFetcher(
                "createTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input = env.getArgument<Map<String, Any?>>("input")
                            ?: throw IllegalArgumentException("input is required")
                        transactionResolver.createTransaction(input, principal)
                    }
                },
            )
            .dataFetcher(
                "updateTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_UPDATE) { principal ->
                        val id = env.getArgument<String>("id")
                            ?: throw IllegalArgumentException("id is required")
                        val input = env.getArgument<Map<String, Any?>>("input")
                            ?: throw IllegalArgumentException("input is required")
                        transactionResolver.updateTransaction(id, input, principal)
                    }
                },
            )
            .dataFetcher(
                "deleteTransaction",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_DELETE) { principal ->
                        val id = env.getArgument<String>("id")
                            ?: throw IllegalArgumentException("id is required")
                        transactionResolver.deleteTransaction(id, principal)
                    }
                },
            )
            .dataFetcher(
                "importTransactionsFile",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val input = env.getArgument<Map<String, Any?>>("input")
                            ?: throw IllegalArgumentException("input is required")
                        importResolver.importTransactionsFile(input, principal)
                    }
                },
            )
            .dataFetcher(
                "importNfce",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_TRANSACTION_CREATE) { principal ->
                        val financialAccountId = env.getArgument<String>("financialAccountId")
                            ?: throw IllegalArgumentException("financialAccountId is required")
                        val qrCodeUrl = env.getArgument<String>("qrCodeUrl")
                            ?: throw IllegalArgumentException("qrCodeUrl is required")
                        val input = mapOf(
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
                        val input = env.getArgument<Map<String, Any?>>("input")
                            ?: throw IllegalArgumentException("input is required")
                        importResolver.resolveImportDuplicate(input, principal)
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
}
