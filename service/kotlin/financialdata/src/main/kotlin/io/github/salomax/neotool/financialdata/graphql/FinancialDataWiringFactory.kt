package io.github.salomax.neotool.financialdata.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionSummaryItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.financialdata.graphql.resolver.InstitutionSearchResolver
import jakarta.inject.Singleton

/**
 * Financial Data module wiring factory for GraphQL resolvers.
 */
@Singleton
class FinancialDataWiringFactory(
    private val institutionSearchResolver: InstitutionSearchResolver,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("institutionSearch", institutionSearchResolver)
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "searchBacenInstitutionsAutocomplete",
                createValidatedDataFetcher { env ->
                    val query =
                        env.getArgument<String>("query")
                            ?: throw IllegalArgumentException("Query parameter is required")
                    institutionSearchResolver.searchBacenInstitutionsAutocomplete(query)
                },
            )
            .dataFetcher(
                "bacenInstitutions",
                createValidatedDataFetcher { env ->
                    val query = env.getArgument<String?>("query")
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    institutionSearchResolver.bacenInstitutions(query, first, after)
                },
            )
    }

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        // No mutations for institution search
        return type
    }

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        // No subscriptions for institution search
        return type
    }

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder
            // PageInfo type resolvers
            .type("PageInfo") { type ->
                type.dataFetcher(
                    "hasNextPage",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val pageInfo = env.getSource<PageInfoDTO>()
                        pageInfo?.hasNextPage ?: false
                    },
                )
                    .dataFetcher(
                        "hasPreviousPage",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val pageInfo = env.getSource<PageInfoDTO>()
                            pageInfo?.hasPreviousPage ?: false
                        },
                    )
                    .dataFetcher(
                        "startCursor",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val pageInfo = env.getSource<PageInfoDTO>()
                            pageInfo?.startCursor
                        },
                    )
                    .dataFetcher(
                        "endCursor",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val pageInfo = env.getSource<PageInfoDTO>()
                            pageInfo?.endCursor
                        },
                    )
            }
            // BacenInstitutionEdge type resolvers
            .type("BacenInstitutionEdge") { type ->
                type.dataFetcher(
                    "node",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<BacenInstitutionEdgeDTO>()
                        edge?.node
                    },
                )
                    .dataFetcher(
                        "cursor",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val edge = env.getSource<BacenInstitutionEdgeDTO>()
                            edge?.cursor
                        },
                    )
            }
            // BacenInstitutionSummaryItem type resolvers
            .type("BacenInstitutionSummaryItem") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                        item?.id
                    },
                )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.name
                        },
                    )
                    .dataFetcher(
                        "legalName",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.legalName
                        },
                    )
                    .dataFetcher(
                        "tradeName",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.tradeName
                        },
                    )
                    .dataFetcher(
                        "logoAsset",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            // Return Asset reference for federation
                            // Federation will resolve the Asset entity using the ID
                            item?.assetId?.let { assetId ->
                                mapOf(
                                    "__typename" to "Asset",
                                    "id" to assetId,
                                )
                            }
                        },
                    )
                    .dataFetcher(
                        "profit",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.profit
                        },
                    )
                    .dataFetcher(
                        "equity",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.equity
                        },
                    )
                    .dataFetcher(
                        "yoyEquityGrowth",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.yoyEquityGrowth
                        },
                    )
                    .dataFetcher(
                        "tipoConsolidadoBancario",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.tipoConsolidadoBancario
                        },
                    )
                    .dataFetcher(
                        "basileiaIndex",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.basileiaIndex
                        },
                    )
                    .dataFetcher(
                        "bacenInstitutionType",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.bacenInstitutionType
                        },
                    )
                    .dataFetcher(
                        "institutionType",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                            item?.institutionType?.name
                        },
                    )
            }
            // BacenInstitutionAutocompleteItem type resolvers
            .type("BacenInstitutionAutocompleteItem") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val item = env.getSource<BacenInstitutionAutocompleteItemDTO>()
                        item?.id
                    },
                )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionAutocompleteItemDTO>()
                            item?.name
                        },
                    )
                    .dataFetcher(
                        "cnpj",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionAutocompleteItemDTO>()
                            item?.cnpj
                        },
                    )
                    .dataFetcher(
                        "legalName",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionAutocompleteItemDTO>()
                            item?.legalName
                        },
                    )
                    .dataFetcher(
                        "tradeName",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionAutocompleteItemDTO>()
                            item?.tradeName
                        },
                    )
                    .dataFetcher(
                        "logoAsset",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val item = env.getSource<BacenInstitutionAutocompleteItemDTO>()
                            // Return Asset reference for federation
                            // Federation will resolve the Asset entity using the ID
                            item?.assetId?.let { assetId ->
                                mapOf(
                                    "__typename" to "Asset",
                                    "id" to assetId,
                                )
                            }
                        },
                    )
            }
            // BacenInstitutionConnection type resolvers
            .type("BacenInstitutionConnection") { type ->
                type.dataFetcher(
                    "edges",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<BacenInstitutionConnectionDTO>()
                        connection?.edges
                    },
                )
                    .dataFetcher(
                        "pageInfo",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val connection = env.getSource<BacenInstitutionConnectionDTO>()
                            connection?.pageInfo
                        },
                    )
                    .dataFetcher(
                        "totalCount",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val connection = env.getSource<BacenInstitutionConnectionDTO>()
                            connection?.totalCount
                        },
                    )
            }
            // BacenInstitutionAutocompleteConnection type resolvers
            .type("BacenInstitutionAutocompleteConnection") { type ->
                type.dataFetcher(
                    "suggestions",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<BacenInstitutionAutocompleteConnectionDTO>()
                        connection?.suggestions
                    },
                )
            }
            // Asset type resolver - for federation entity references
            .type("Asset") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        // Asset reference is returned as a map with __typename and id
                        val assetRef = env.getSource<Map<String, Any>>()
                        assetRef?.get("id") as? String
                    },
                )
            }
    }
}
