package io.github.salomax.neotool.financialdata.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.financialdata.graphql.dto.BacenFinanceInstitutionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionSummaryItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InstitutionRatingDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.financialdata.graphql.dto.QuarterReportsDTO
import io.github.salomax.neotool.financialdata.graphql.dto.QuarterValueDTO
import io.github.salomax.neotool.financialdata.graphql.dto.RatingHistoryDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportAccountDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportDescriptorDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportDetailsDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportsAvailabilityDTO
import io.github.salomax.neotool.financialdata.graphql.resolver.BacenInstitutionDetailResolver
import io.github.salomax.neotool.financialdata.graphql.resolver.FinancialInstitutionRatingResolver
import io.github.salomax.neotool.financialdata.graphql.resolver.InstitutionSearchResolver
import jakarta.inject.Singleton
import java.time.ZoneOffset

/**
 * Financial Data module wiring factory for GraphQL resolvers.
 */
@Singleton
class FinancialDataWiringFactory(
    private val institutionSearchResolver: InstitutionSearchResolver,
    private val institutionDetailResolver: BacenInstitutionDetailResolver,
    private val ratingResolver: FinancialInstitutionRatingResolver,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("institutionSearch", institutionSearchResolver)
        resolverRegistry.register("institutionDetail", institutionDetailResolver)
        resolverRegistry.register("ratingResolver", ratingResolver)
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "searchBacenInstitutionsAutocomplete",
                createValidatedDataFetcher { env ->
                    val query =
                        env.getArgument<String>("query")
                            ?: throw IllegalArgumentException("Query parameter is required")
                    val anoMes = env.getArgument<String?>("anoMes")
                    institutionSearchResolver.searchBacenInstitutionsAutocomplete(query, anoMes)
                },
            )
            .dataFetcher(
                "bacenInstitutions",
                createValidatedDataFetcher { env ->
                    val query = env.getArgument<String?>("query")
                    val anoMes = env.getArgument<String?>("anoMes")
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    institutionSearchResolver.bacenInstitutions(query, anoMes, first, after)
                },
            )
            .dataFetcher(
                "bacenFinanceInstitution",
                createValidatedDataFetcher { env ->
                    val id =
                        env.getArgument<String>("id")
                            ?: throw IllegalArgumentException("id parameter is required")
                    val anoMes = env.getArgument<String?>("anoMes")
                    institutionDetailResolver.bacenFinanceInstitution(id, anoMes)
                },
            )
            .dataFetcher(
                "ratingIssuers",
                createValidatedDataFetcher { env ->
                    ratingResolver.ratingIssuers(env)
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
                    "name",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val item = env.getSource<BacenInstitutionAutocompleteItemDTO>()
                        item?.name
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
            // BacenFinanceInstitution type resolvers
            .type("BacenFinanceInstitution") { type ->
                // Base fields from BacenInstitutionSummaryItem
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val institution = env.getSource<BacenFinanceInstitutionDTO>()
                        institution?.id
                    },
                )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.name
                        },
                    )
                    .dataFetcher(
                        "legalName",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.legalName
                        },
                    )
                    .dataFetcher(
                        "tradeName",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.tradeName
                        },
                    )
                    .dataFetcher(
                        "logoAsset",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.assetId?.let { assetId ->
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
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.profit
                        },
                    )
                    .dataFetcher(
                        "equity",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.equity
                        },
                    )
                    .dataFetcher(
                        "yoyEquityGrowth",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.yoyEquityGrowth
                        },
                    )
                    .dataFetcher(
                        "basileiaIndex",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.basileiaIndex
                        },
                    )
                    .dataFetcher(
                        "institutionType",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.institutionType?.name
                        },
                    )
                    .dataFetcher(
                        "tipoConsolidadoBancario",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.tipoConsolidadoBancario?.name
                        },
                    )
                    // New fields specific to BacenFinanceInstitution
                    .dataFetcher(
                        "quarter",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.quarter
                        },
                    )
                    .dataFetcher(
                        "asset",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            // Return Asset reference for federation
                            // Federation will resolve the Asset entity using the ID
                            institution?.assetId?.let { assetId ->
                                mapOf(
                                    "__typename" to "Asset",
                                    "id" to assetId,
                                )
                            }
                        },
                    )
                    .dataFetcher(
                        "website",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.website
                        },
                    )
                    .dataFetcher(
                        "socialNetworks",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.socialNetworks
                        },
                    )
                    .dataFetcher(
                        "mainActivity",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.mainActivity
                        },
                    )
                    .dataFetcher(
                        "address",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.address
                        },
                    )
                    .dataFetcher(
                        "founded",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.founded
                        },
                    )
                    .dataFetcher(
                        "controlType",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.controlType?.name
                        },
                    )
                    .dataFetcher(
                        "institutionBankType",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.institutionBankType?.name
                        },
                    )
                    .dataFetcher(
                        "mobilizacaoIndex",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.mobilizacaoIndex
                        },
                    )
                    .dataFetcher(
                        "totalAssets",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.totalAssets
                        },
                    )
                    .dataFetcher(
                        "funding",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.funding
                        },
                    )
                    .dataFetcher(
                        "loanPortfolio",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.loanPortfolio
                        },
                    )
                    .dataFetcher(
                        "rwaReferenceEquity",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val institution = env.getSource<BacenFinanceInstitutionDTO>()
                            institution?.rwaReferenceEquity
                        },
                    )
                    .dataFetcher(
                        "reportsSummary",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            institutionDetailResolver.reportsSummary(env)
                        },
                    )
                    .dataFetcher(
                        "reportDetails",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            institutionDetailResolver.reportDetails(env)
                        },
                    )
                    .dataFetcher(
                        "ratings",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            ratingResolver.ratings(env)
                        },
                    )
                    .dataFetcher(
                        "ratingHistory",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            ratingResolver.ratingHistory(env)
                        },
                    )
            }
            // ReportsAvailability type resolvers
            .type("ReportsAvailability") { type ->
                type.dataFetcher(
                    "quarters",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val availability = env.getSource<ReportsAvailabilityDTO>()
                        availability?.quarters
                    },
                )
            }
            // QuarterReports type resolvers
            .type("QuarterReports") { type ->
                type.dataFetcher(
                    "quarter",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val quarterReports = env.getSource<QuarterReportsDTO>()
                        quarterReports?.quarter
                    },
                )
                    .dataFetcher(
                        "reports",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val quarterReports = env.getSource<QuarterReportsDTO>()
                            quarterReports?.reports
                        },
                    )
            }
            // ReportDescriptor type resolvers
            .type("ReportDescriptor") { type ->
                type.dataFetcher(
                    "reportId",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val descriptor = env.getSource<ReportDescriptorDTO>()
                        descriptor?.reportId
                    },
                )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val descriptor = env.getSource<ReportDescriptorDTO>()
                            descriptor?.name
                        },
                    )
                    .dataFetcher(
                        "group",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val descriptor = env.getSource<ReportDescriptorDTO>()
                            descriptor?.group
                        },
                    )
                    .dataFetcher(
                        "id",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val descriptor = env.getSource<ReportDescriptorDTO>()
                            descriptor?.accountId
                        },
                    )
            }
            // ReportDetails type resolvers
            .type("ReportDetails") { type ->
                type.dataFetcher(
                    "reportId",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val details = env.getSource<ReportDetailsDTO>()
                        details?.reportId
                    },
                )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val details = env.getSource<ReportDetailsDTO>()
                            details?.name
                        },
                    )
                    .dataFetcher(
                        "accounts",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val details = env.getSource<ReportDetailsDTO>()
                            details?.accounts
                        },
                    )
            }
            // ReportAccount type resolvers
            .type("ReportAccount") { type ->
                type.dataFetcher(
                    "group",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val account = env.getSource<ReportAccountDTO>()
                        account?.group
                    },
                )
                    .dataFetcher(
                        "id",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val account = env.getSource<ReportAccountDTO>()
                            account?.accountId
                        },
                    )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val account = env.getSource<ReportAccountDTO>()
                            account?.accountName
                        },
                    )
                    .dataFetcher(
                        "valuesByQuarter",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val account = env.getSource<ReportAccountDTO>()
                            account?.valuesByQuarter
                        },
                    )
            }
            // QuarterValue type resolvers
            .type("QuarterValue") { type ->
                type.dataFetcher(
                    "quarter",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val value = env.getSource<QuarterValueDTO>()
                        value?.quarter
                    },
                )
                    .dataFetcher(
                        "value",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val value = env.getSource<QuarterValueDTO>()
                        value?.value
                    },
                )
            }
            .type("InstitutionRating") { type ->
                type.dataFetcher(
                    "updatedAt",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val rating = env.getSource<InstitutionRatingDTO>()
                        // Convert Instant to OffsetDateTime for GraphQL DateTime scalar
                        rating?.updatedAt?.atOffset(ZoneOffset.UTC)
                    },
                )
            }
            .type("RatingHistory") { type ->
                type.dataFetcher(
                    "changedAt",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val history = env.getSource<RatingHistoryDTO>()
                        // Convert Instant to OffsetDateTime for GraphQL DateTime scalar
                        history?.changedAt?.atOffset(ZoneOffset.UTC)
                    },
                )
            }
    }
}
