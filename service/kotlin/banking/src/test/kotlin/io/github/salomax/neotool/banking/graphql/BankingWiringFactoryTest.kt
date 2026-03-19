package io.github.salomax.neotool.banking.graphql

import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingEnvironmentImpl
import io.github.salomax.neotool.banking.domain.issuer.IssuerCategory
import io.github.salomax.neotool.banking.domain.rbac.BankingPermissions
import io.github.salomax.neotool.banking.graphql.resolver.FinancialAccountResolver
import io.github.salomax.neotool.banking.graphql.resolver.ImportResolver
import io.github.salomax.neotool.banking.graphql.resolver.InvestmentResolver
import io.github.salomax.neotool.banking.graphql.resolver.IssuerResolver
import io.github.salomax.neotool.banking.graphql.resolver.BillImportResolver
import io.github.salomax.neotool.banking.graphql.resolver.TransactionCategoryResolver
import io.github.salomax.neotool.banking.graphql.resolver.TransactionResolver
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import io.github.salomax.neotool.common.security.principal.TokenPrincipalDecoder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@DisplayName("BankingWiringFactory permissions")
class BankingWiringFactoryTest {
    private lateinit var issuerResolver: IssuerResolver
    private lateinit var financialAccountResolver: FinancialAccountResolver
    private lateinit var transactionResolver: TransactionResolver
    private lateinit var transactionCategoryResolver: TransactionCategoryResolver
    private lateinit var importResolver: ImportResolver
    private lateinit var pdfImportResolver: BillImportResolver
    private lateinit var investmentResolver: InvestmentResolver
    private lateinit var authorizationChecker: AuthorizationChecker
    private lateinit var requestPrincipalProvider: RequestPrincipalProvider
    private lateinit var wiringFactory: BankingWiringFactory

    private val userId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        issuerResolver = mock()
        financialAccountResolver = mock()
        transactionResolver = mock()
        transactionCategoryResolver = mock()
        importResolver = mock()
        pdfImportResolver = mock()
        investmentResolver = mock()
        authorizationChecker = mock()
        whenever(issuerResolver.listIssuers(anyOrNull())).thenReturn(emptyList())

        val decoder =
            TokenPrincipalDecoder { token ->
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = userId,
                    serviceId = null,
                    token = token,
                    permissionsFromToken = emptyList(),
                )
            }
        requestPrincipalProvider = RequestPrincipalProvider(decoder)

        wiringFactory =
            BankingWiringFactory(
                issuerResolver,
                financialAccountResolver,
                transactionResolver,
                transactionCategoryResolver,
                investmentResolver,
                importResolver,
                pdfImportResolver,
                requestPrincipalProvider,
                authorizationChecker,
            )
    }

    private fun envWithToken(
        token: String = "test-token",
        arguments: Map<String, Any?> = emptyMap(),
    ): DataFetchingEnvironment {
        val context = GraphQLContext.newContext().of("token", token).build()
        return DataFetchingEnvironmentImpl
            .newDataFetchingEnvironment()
            .graphQLContext(context)
            .arguments(arguments)
            .build()
    }

    @Nested
    @DisplayName("Query permissions")
    inner class QueryPermissionsTests {
        @Test
        fun `issuers requires banking_issuer_view permission`() {
            val wiring = wiringFactory.build()
            val dataFetcher = wiring.dataFetchers["Query"]!!["issuers"]!!

            val env = envWithToken()

            dataFetcher.get(env)

            verify(authorizationChecker).require(any(), eq(BankingPermissions.BANKING_ISSUER_VIEW))
            verify(issuerResolver).listIssuers(eq(null))
        }

        @Test
        fun `issuers forwards categories argument to resolver`() {
            val wiring = wiringFactory.build()
            val dataFetcher = wiring.dataFetchers["Query"]!!["issuers"]!!

            val env =
                envWithToken(
                    arguments =
                        mapOf(
                            "categories" to listOf("BANK", "BROKERAGE"),
                        ),
                )

            dataFetcher.get(env)

            verify(issuerResolver).listIssuers(eq(listOf(IssuerCategory.BANK, IssuerCategory.BROKERAGE)))
            verify(authorizationChecker).require(any(), eq(BankingPermissions.BANKING_ISSUER_VIEW))
        }

        @Test
        fun `financialAccounts requires banking_account_view permission`() {
            val wiring = wiringFactory.build()
            val dataFetcher = wiring.dataFetchers["Query"]!!["financialAccounts"]!!

            val env = envWithToken()

            dataFetcher.get(env)

            verify(authorizationChecker).require(any(), eq(BankingPermissions.BANKING_ACCOUNT_VIEW))
        }
    }

    @Nested
    @DisplayName("Mutation permissions")
    inner class MutationPermissionsTests {
        @Test
        fun `createFinancialAccount requires banking_account_create permission`() {
            val wiring = wiringFactory.build()
            val dataFetcher = wiring.dataFetchers["Mutation"]!!["createFinancialAccount"]!!

            val env =
                envWithToken(
                    arguments =
                        mapOf(
                            "input" to emptyMap<String, Any?>(),
                        ),
                )

            dataFetcher.get(env)

            verify(authorizationChecker).require(any(), eq(BankingPermissions.BANKING_ACCOUNT_CREATE))
        }

        @Test
        fun `submitBillImport requires banking_transaction_create permission`() {
            val wiring = wiringFactory.build()
            val dataFetcher = wiring.dataFetchers["Mutation"]!!["submitBillImport"]!!

            val env =
                envWithToken(
                    arguments = mapOf("input" to emptyMap<String, Any?>()),
                )

            dataFetcher.get(env)

            verify(authorizationChecker).require(any(), eq(BankingPermissions.BANKING_TRANSACTION_CREATE))
        }

        @Test
        fun `confirmBillImport requires banking_transaction_create permission`() {
            val wiring = wiringFactory.build()
            val dataFetcher = wiring.dataFetchers["Mutation"]!!["confirmBillImport"]!!

            val env =
                envWithToken(
                    arguments = mapOf("input" to emptyMap<String, Any?>()),
                )

            dataFetcher.get(env)

            verify(authorizationChecker).require(any(), eq(BankingPermissions.BANKING_TRANSACTION_CREATE))
        }

        @Test
        fun `createInvestmentAsset requires banking_investment_create permission`() {
            val wiring = wiringFactory.build()
            val dataFetcher = wiring.dataFetchers["Mutation"]!!["createInvestmentAsset"]!!

            val env =
                envWithToken(
                    arguments =
                        mapOf(
                            "input" to
                                mapOf(
                                    "name" to "CDB Banco X 120 CDI",
                                    "assetType" to "CDB",
                                ),
                        ),
                )

            dataFetcher.get(env)

            verify(authorizationChecker).require(any(), eq(BankingPermissions.BANKING_INVESTMENT_CREATE))
        }
    }
}
