package io.github.salomax.neotool.financialdata.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.financialdata.graphql.dataloader.IndicatorSeriesDataLoader
import io.github.salomax.neotool.financialdata.graphql.dto.AssetDailyPriceDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenFinanceInstitutionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionSlugConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionSlugDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionSlugEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionSummaryItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.DataRestrictionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorVariationDTO
import io.github.salomax.neotool.financialdata.graphql.dto.EconomicIndicatorVariationSeriesDTO
import io.github.salomax.neotool.financialdata.graphql.dto.FundDailyNavDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InstitutionRatingDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundEdgeDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InvestmentFundMarketSnapshotDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetEdgeDTO
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
import io.github.salomax.neotool.financialdata.graphql.resolver.FixedIncomeComparisonResolver
import io.github.salomax.neotool.financialdata.graphql.resolver.IndicatorResolver
import io.github.salomax.neotool.financialdata.graphql.resolver.InstitutionDiscoveryResolver
import io.github.salomax.neotool.financialdata.graphql.resolver.InstitutionSearchResolver
import io.github.salomax.neotool.financialdata.graphql.resolver.InvestmentAssetResolver
import io.github.salomax.neotool.financialdata.graphql.resolver.InvestmentFundResolver
import io.github.salomax.neotool.financialdata.graphql.resolver.MarketDataSyncResolver
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Financial Data module wiring factory for GraphQL resolvers.
 */
@Singleton
class FinancialDataWiringFactory(
    private val institutionSearchResolver: InstitutionSearchResolver,
    private val institutionDetailResolver: BacenInstitutionDetailResolver,
    private val institutionDiscoveryResolver: InstitutionDiscoveryResolver,
    private val ratingResolver: FinancialInstitutionRatingResolver,
    private val indicatorResolver: IndicatorResolver,
    private val fixedIncomeComparisonResolver: FixedIncomeComparisonResolver,
    private val investmentFundResolver: InvestmentFundResolver,
    private val investmentAssetResolver: InvestmentAssetResolver,
    private val marketDataSyncResolver: MarketDataSyncResolver,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("institutionSearch", institutionSearchResolver)
        resolverRegistry.register("institutionDetail", institutionDetailResolver)
        resolverRegistry.register("institutionDiscovery", institutionDiscoveryResolver)
        resolverRegistry.register("ratingResolver", ratingResolver)
        resolverRegistry.register("indicatorResolver", indicatorResolver)
        resolverRegistry.register("fixedIncomeComparison", fixedIncomeComparisonResolver)
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
                "bacenInstitutionSlugs",
                createValidatedDataFetcher { env ->
                    val anoMes = env.getArgument<String?>("anoMes")
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    institutionSearchResolver.bacenInstitutionSlugs(anoMes, first, after)
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
                "popularInstitutions",
                createValidatedDataFetcher { env ->
                    val anoMes = env.getArgument<String?>("anoMes")
                    institutionDiscoveryResolver.popularInstitutions(anoMes)
                },
            )
            .dataFetcher(
                "ratingIssuers",
                createValidatedDataFetcher { env ->
                    ratingResolver.ratingIssuers(env)
                },
            )
            .dataFetcher(
                "economicIndicators",
                createValidatedDataFetcher { env ->
                    indicatorResolver.economicIndicators(env)
                },
            )
            .dataFetcher(
                "indicatorSeries",
                createValidatedDataFetcher { env ->
                    indicatorResolver.indicatorSeries(env)
                },
            )
            .dataFetcher(
                "indicatorLatest",
                createValidatedDataFetcher { env ->
                    indicatorResolver.indicatorLatest(env)
                },
            )
            .dataFetcher(
                "indicatorsLatest",
                createValidatedDataFetcher { env ->
                    indicatorResolver.indicatorsLatest(env)
                },
            )
            .dataFetcher(
                "fixedIncomeComparison",
                createValidatedDataFetcher { env ->
                    fixedIncomeComparisonResolver.fixedIncomeComparison(env)
                },
            )
            .dataFetcher(
                "searchInvestmentFundsAutocomplete",
                createValidatedDataFetcher { env ->
                    val query: String =
                        env.getArgument<String?>("query")
                            ?: throw IllegalArgumentException("query is required")
                    investmentFundResolver.searchInvestmentFundsAutocomplete(query)
                },
            )
            .dataFetcher(
                "investmentFunds",
                createValidatedDataFetcher { env ->
                    val query = env.getArgument<String?>("query")
                    val assetType = env.getArgument<String?>("assetType")
                    val isActive = env.getArgument<Boolean?>("isActive")
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    investmentFundResolver.investmentFunds(query, assetType, isActive, first, after)
                },
            )
            .dataFetcher(
                "investmentFund",
                createValidatedDataFetcher { env ->
                    val id: String =
                        env.getArgument<String?>("id")
                            ?: throw IllegalArgumentException("id is required")
                    investmentFundResolver.investmentFund(id)
                },
            )
            .dataFetcher(
                "investmentFundMarketSnapshots",
                createValidatedDataFetcher { env ->
                    val cnpjs =
                        env.getArgument<List<String>?>("cnpjs")
                            ?: throw IllegalArgumentException("cnpjs is required")
                    investmentFundResolver.investmentFundMarketSnapshots(cnpjs)
                },
            )
            .dataFetcher(
                "fundNavSeries",
                createValidatedDataFetcher { env ->
                    val cnpj =
                        env.getArgument<String?>("cnpj")
                            ?: throw IllegalArgumentException("cnpj is required")
                    val from =
                        env.getArgument<LocalDate?>("from")
                            ?: throw IllegalArgumentException("from is required")
                    val to =
                        env.getArgument<LocalDate?>("to")
                            ?: throw IllegalArgumentException("to is required")
                    investmentFundResolver.fundNavSeries(cnpj, from, to)
                },
            )
            .dataFetcher(
                "fundDailyNavs",
                createValidatedDataFetcher { env ->
                    marketDataSyncResolver.fundDailyNavs(
                        from =
                            env.getArgument<LocalDate?>("from")
                                ?: throw IllegalArgumentException("from is required"),
                        to =
                            env.getArgument<LocalDate?>("to")
                                ?: throw IllegalArgumentException("to is required"),
                        first = env.getArgument<Int?>("first"),
                        after = env.getArgument<String?>("after"),
                        updatedSince = env.getArgument<Instant?>("updatedSince"),
                        cnpjs = env.getArgument<List<String>?>("cnpjs"),
                    )
                },
            )
            .dataFetcher(
                "searchInvestmentAssetsAutocomplete",
                createValidatedDataFetcher { env ->
                    val query: String =
                        env.getArgument<String?>("query")
                            ?: throw IllegalArgumentException("query is required")
                    val assetType = env.getArgument<String?>("assetType")
                    investmentAssetResolver.searchInvestmentAssetsAutocomplete(query, assetType)
                },
            )
            .dataFetcher(
                "investmentAssets",
                createValidatedDataFetcher { env ->
                    val query = env.getArgument<String?>("query")
                    val assetType = env.getArgument<String?>("assetType")
                    val isActive = env.getArgument<Boolean?>("isActive")
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    investmentAssetResolver.investmentAssets(query, assetType, isActive, first, after)
                },
            )
            .dataFetcher(
                "investmentAssetByTicker",
                createValidatedDataFetcher { env ->
                    val ticker: String =
                        env.getArgument<String?>("ticker")
                            ?: throw IllegalArgumentException("ticker is required")
                    investmentAssetResolver.investmentAssetByTicker(ticker)
                },
            )
            .dataFetcher(
                "investmentAssetsByTickers",
                createValidatedDataFetcher { env ->
                    val tickers: List<String> =
                        env.getArgument<List<String>?>("tickers")
                            ?: throw IllegalArgumentException("tickers is required")
                    investmentAssetResolver.investmentAssetsByTickers(tickers)
                },
            )
            .dataFetcher(
                "listedAssetDailyPrices",
                createValidatedDataFetcher { env ->
                    marketDataSyncResolver.listedAssetDailyPrices(
                        from =
                            env.getArgument<LocalDate?>("from")
                                ?: throw IllegalArgumentException("from is required"),
                        to =
                            env.getArgument<LocalDate?>("to")
                                ?: throw IllegalArgumentException("to is required"),
                        first = env.getArgument<Int?>("first"),
                        after = env.getArgument<String?>("after"),
                        updatedSince = env.getArgument<Instant?>("updatedSince"),
                        tickers = env.getArgument<List<String>?>("tickers"),
                    )
                },
            )
            .dataFetcher(
                "economicIndicatorVariationPoints",
                createValidatedDataFetcher { env ->
                    marketDataSyncResolver.economicIndicatorVariationPoints(
                        from =
                            env.getArgument<LocalDate?>("from")
                                ?: throw IllegalArgumentException("from is required"),
                        to =
                            env.getArgument<LocalDate?>("to")
                                ?: throw IllegalArgumentException("to is required"),
                        first = env.getArgument<Int?>("first"),
                        after = env.getArgument<String?>("after"),
                        updatedSince = env.getArgument<Instant?>("updatedSince"),
                        indicators = env.getArgument<List<String>?>("indicators"),
                        variations = env.getArgument<List<String>?>("variations"),
                    )
                },
            )
            .dataFetcher(
                "assetCorporateEvents",
                createValidatedDataFetcher { env ->
                    marketDataSyncResolver.assetCorporateEvents(
                        from =
                            env.getArgument<LocalDate?>("from")
                                ?: throw IllegalArgumentException("from is required"),
                        to =
                            env.getArgument<LocalDate?>("to")
                                ?: throw IllegalArgumentException("to is required"),
                        first = env.getArgument<Int?>("first"),
                        after = env.getArgument<String?>("after"),
                        updatedSince = env.getArgument<Instant?>("updatedSince"),
                        tickers = env.getArgument<List<String>?>("tickers"),
                        isins = env.getArgument<List<String>?>("isins"),
                    )
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
        val withBaseTypes =
            builder
                // EconomicIndicator type resolvers
                .type("EconomicIndicator") { type ->
                    type.dataFetcher(
                        "variations",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            indicatorResolver.variations(env)
                        },
                    )
                }
                // EconomicIndicatorVariation type resolvers
                .type("EconomicIndicatorVariation") { type ->
                    type.dataFetcher(
                        "series",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val source =
                                env.getSource<EconomicIndicatorVariationDTO>()
                                    ?: throw IllegalStateException("Source is null")
                            val indicatorLabel =
                                source.indicatorLabel
                                    ?: throw IllegalStateException("Indicator label is missing")

                            val range = env.getArgument<Map<String, Any>?>("range")
                            val limit = env.getArgument<Int?>("limit")

                            val fromDateStr = range?.get("from")
                            val toDateStr = range?.get("to")

                            val (from, to) =
                                if (fromDateStr == null && toDateStr == null) {
                                    val now = LocalDate.now()
                                    now.minusYears(5) to now
                                } else {
                                    val f =
                                        if (fromDateStr is String) {
                                            LocalDate.parse(fromDateStr)
                                        } else {
                                            LocalDate.of(1900, 1, 1)
                                        }
                                    val t =
                                        if (toDateStr is String) {
                                            LocalDate.parse(toDateStr)
                                        } else {
                                            LocalDate.now()
                                        }
                                    f to t
                                }

                            val dataLoader =
                                env.getDataLoader<
                                    IndicatorSeriesDataLoader.Key,
                                    EconomicIndicatorVariationSeriesDTO?,
                                >(IndicatorSeriesDataLoader.KEY)
                                    ?: throw IllegalStateException(
                                        "DataLoader ${IndicatorSeriesDataLoader.KEY} not found",
                                    )

                            dataLoader.load(
                                IndicatorSeriesDataLoader.Key(
                                    indicator = indicatorLabel,
                                    variation = source.label,
                                    from = from,
                                    to = to,
                                    limit = limit,
                                ),
                            )
                        },
                    )
                }
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
                // FinancialDataPageInfo: same shape as PageInfo, used by BacenInstitutionSlugConnection
                // so the router only calls this subgraph (no shared type with SECURITY).
                .type("FinancialDataPageInfo") { type ->
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
                        .dataFetcher(
                            "dataRestriction",
                            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                                val item = env.getSource<BacenInstitutionSummaryItemDTO>()
                                item?.dataRestriction
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
                // BacenInstitutionSlugConnection type resolvers
                .type("BacenInstitutionSlugConnection") { type ->
                    type.dataFetcher(
                        "edges",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val connection = env.getSource<BacenInstitutionSlugConnectionDTO>()
                            connection?.edges
                        },
                    )
                        .dataFetcher(
                            "pageInfo",
                            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                                val connection = env.getSource<BacenInstitutionSlugConnectionDTO>()
                                connection?.pageInfo
                            },
                        )
                }
                // BacenInstitutionSlugEdge type resolvers
                .type("BacenInstitutionSlugEdge") { type ->
                    type.dataFetcher(
                        "node",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val edge = env.getSource<BacenInstitutionSlugEdgeDTO>()
                            edge?.node
                        },
                    )
                        .dataFetcher(
                            "cursor",
                            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                                val edge = env.getSource<BacenInstitutionSlugEdgeDTO>()
                                edge?.cursor
                            },
                        )
                }
                // BacenInstitutionSlug type resolvers
                .type("BacenInstitutionSlug") { type ->
                    type.dataFetcher(
                        "id",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val slug = env.getSource<BacenInstitutionSlugDTO>()
                            slug?.id
                        },
                    )
                        .dataFetcher(
                            "name",
                            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                                val slug = env.getSource<BacenInstitutionSlugDTO>()
                                slug?.name
                            },
                        )
                        .dataFetcher(
                            "updatedAt",
                            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                                val slug = env.getSource<BacenInstitutionSlugDTO>()
                                slug?.updatedAt?.atOffset(ZoneOffset.UTC)
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
                            "updatedAt",
                            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                                val institution = env.getSource<BacenFinanceInstitutionDTO>()
                                institution?.updatedAt?.atOffset(ZoneOffset.UTC)
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
                            "dataRestriction",
                            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                                val institution = env.getSource<BacenFinanceInstitutionDTO>()
                                institution?.dataRestriction
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
                // DataRestriction type resolvers
                .type("DataRestriction") { type ->
                    type.dataFetcher(
                        "isRestricted",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val restriction = env.getSource<DataRestrictionDTO>()
                            restriction?.isRestricted
                        },
                    )
                        .dataFetcher(
                            "reason",
                            createValidatedDataFetcher { env: DataFetchingEnvironment ->
                                val restriction = env.getSource<DataRestrictionDTO>()
                                restriction?.reason
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
        return registerInvestmentFundTypeResolvers(withBaseTypes)
    }

    private fun registerInvestmentFundTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder
            .type("InvestmentFundAutocompleteConnection") { type ->
                type.dataFetcher(
                    "suggestions",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        env.getSource<InvestmentFundAutocompleteConnectionDTO>()?.suggestions ?: emptyList()
                    },
                )
            }
            .type("InvestmentFundAutocompleteItem") { type ->
                type
                    .dataFetcher(
                        "id",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundAutocompleteItemDTO>()?.id
                        },
                    )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundAutocompleteItemDTO>()?.name
                        },
                    )
                    .dataFetcher(
                        "cnpj",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundAutocompleteItemDTO>()?.cnpj
                        },
                    )
                    .dataFetcher(
                        "assetType",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundAutocompleteItemDTO>()?.assetType
                        },
                    )
                    .dataFetcher(
                        "isActive",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundAutocompleteItemDTO>()?.isActive
                        },
                    )
            }
            .type("InvestmentFundConnection") { type ->
                type
                    .dataFetcher(
                        "edges",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundConnectionDTO>()?.edges ?: emptyList()
                        },
                    )
                    .dataFetcher(
                        "pageInfo",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundConnectionDTO>()?.pageInfo
                        },
                    )
                    .dataFetcher(
                        "totalCount",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundConnectionDTO>()?.totalCount
                        },
                    )
            }
            .type("InvestmentFundEdge") { type ->
                type
                    .dataFetcher(
                        "node",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundEdgeDTO>()?.node
                        },
                    )
                    .dataFetcher(
                        "cursor",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundEdgeDTO>()?.cursor
                        },
                    )
            }
            .type("InvestmentFund") { type ->
                type
                    .dataFetcher(
                        "id",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.id
                        },
                    )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.name
                        },
                    )
                    .dataFetcher(
                        "cnpj",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.cnpj
                        },
                    )
                    .dataFetcher(
                        "assetType",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.assetType
                        },
                    )
                    .dataFetcher(
                        "isActive",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.isActive
                        },
                    )
                    .dataFetcher(
                        "patrimonioLiquido",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.patrimonioLiquido
                        },
                    )
                    .dataFetcher(
                        "dataPatrimonioLiquido",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.dataPatrimonioLiquido
                        },
                    )
                    .dataFetcher(
                        "custodiante",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.custodiante
                        },
                    )
                    .dataFetcher(
                        "cnpjCustodiante",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.cnpjCustodiante
                        },
                    )
                    .dataFetcher(
                        "auditor",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.auditor
                        },
                    )
                    .dataFetcher(
                        "cnpjAuditor",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.cnpjAuditor
                        },
                    )
                    .dataFetcher(
                        "publicoAlvo",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.publicoAlvo
                        },
                    )
                    .dataFetcher(
                        "taxProfile",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.taxProfile
                        },
                    )
                    .dataFetcher(
                        "latestNav",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundDTO>()?.latestNav
                        },
                    )
                    .dataFetcher(
                        "priceHistory",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val fund = env.getSource<InvestmentFundDTO>()
                            if (fund == null) {
                                emptyList<FundDailyNavDTO>()
                            } else {
                                // GraphQL `Date` scalar is wired to `java.time.LocalDate` (ExtendedScalars.Date),
                                // so we must read these arguments as LocalDate (not String).
                                val from = env.getArgument<LocalDate?>("from")
                                val to = env.getArgument<LocalDate?>("to")
                                val last = env.getArgument<Int?>("last")
                                investmentFundResolver.priceHistory(fund.id, from, to, last)
                            }
                        },
                    )
            }
            .type("InvestmentFundMarketSnapshot") { type ->
                type
                    .dataFetcher(
                        "cnpj",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundMarketSnapshotDTO>()?.cnpj
                        },
                    )
                    .dataFetcher(
                        "assetType",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundMarketSnapshotDTO>()?.assetType
                        },
                    )
                    .dataFetcher(
                        "taxProfile",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundMarketSnapshotDTO>()?.taxProfile
                        },
                    )
                    .dataFetcher(
                        "latestNav",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<InvestmentFundMarketSnapshotDTO>()?.latestNav
                        },
                    )
            }
            .type("FundDailyNav") { type ->
                type
                    .dataFetcher(
                        "date",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<FundDailyNavDTO>()?.date
                        },
                    )
                    .dataFetcher(
                        "closePrice",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<FundDailyNavDTO>()?.closePrice
                        },
                    )
                    .dataFetcher(
                        "totalNetAssets",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<FundDailyNavDTO>()?.totalNetAssets
                        },
                    )
            }
            .type("ListedAssetAutocompleteConnection") { type ->
                type.dataFetcher(
                    "suggestions",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        env.getSource<ListedAssetAutocompleteConnectionDTO>()?.suggestions ?: emptyList()
                    },
                )
            }
            .type("ListedAssetAutocompleteItem") { type ->
                type
                    .dataFetcher(
                        "id",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetAutocompleteItemDTO>()?.id
                        },
                    )
                    .dataFetcher(
                        "ticker",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetAutocompleteItemDTO>()?.ticker
                        },
                    )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetAutocompleteItemDTO>()?.name
                        },
                    )
                    .dataFetcher(
                        "assetType",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetAutocompleteItemDTO>()?.assetType
                        },
                    )
                    .dataFetcher(
                        "isActive",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetAutocompleteItemDTO>()?.isActive
                        },
                    )
                    .dataFetcher(
                        "logoAsset",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetAutocompleteItemDTO>()?.logoAssetId?.let { assetId ->
                                mapOf(
                                    "__typename" to "Asset",
                                    "id" to assetId,
                                )
                            }
                        },
                    )
            }
            .type("ListedAssetConnection") { type ->
                type
                    .dataFetcher(
                        "edges",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetConnectionDTO>()?.edges ?: emptyList()
                        },
                    )
                    .dataFetcher(
                        "pageInfo",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetConnectionDTO>()?.pageInfo
                        },
                    )
                    .dataFetcher(
                        "totalCount",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetConnectionDTO>()?.totalCount
                        },
                    )
            }
            .type("ListedAssetEdge") { type ->
                type
                    .dataFetcher(
                        "node",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetEdgeDTO>()?.node
                        },
                    )
                    .dataFetcher(
                        "cursor",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetEdgeDTO>()?.cursor
                        },
                    )
            }
            .type("ListedAsset") { type ->
                type
                    .dataFetcher(
                        "id",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetDTO>()?.id
                        },
                    )
                    .dataFetcher(
                        "ticker",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetDTO>()?.ticker
                        },
                    )
                    .dataFetcher(
                        "name",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetDTO>()?.name
                        },
                    )
                    .dataFetcher(
                        "assetType",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetDTO>()?.assetType
                        },
                    )
                    .dataFetcher(
                        "exchange",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetDTO>()?.exchange
                        },
                    )
                    .dataFetcher(
                        "isActive",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetDTO>()?.isActive
                        },
                    )
                    .dataFetcher(
                        "logoAsset",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetDTO>()?.logoAssetId?.let { assetId ->
                                mapOf(
                                    "__typename" to "Asset",
                                    "id" to assetId,
                                )
                            }
                        },
                    )
                    .dataFetcher(
                        "latestPrice",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<ListedAssetDTO>()?.latestPrice
                        },
                    )
                    .dataFetcher(
                        "priceHistory",
                        createValidatedDataFetcher { env: DataFetchingEnvironment ->
                            val asset = env.getSource<ListedAssetDTO>()
                            if (asset == null) {
                                emptyList<AssetDailyPriceDTO>()
                            } else {
                                val from = env.getArgument<LocalDate?>("from")
                                val to = env.getArgument<LocalDate?>("to")
                                val last = env.getArgument<Int?>("last")
                                investmentAssetResolver.priceHistory(asset.id, from, to, last)
                            }
                        },
                    )
            }
            .type("AssetDailyPrice") { type ->
                type
                    .dataFetcher(
                        "date",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<AssetDailyPriceDTO>()?.date
                        },
                    )
                    .dataFetcher(
                        "openPrice",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<AssetDailyPriceDTO>()?.openPrice
                        },
                    )
                    .dataFetcher(
                        "highPrice",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<AssetDailyPriceDTO>()?.highPrice
                        },
                    )
                    .dataFetcher(
                        "lowPrice",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<AssetDailyPriceDTO>()?.lowPrice
                        },
                    )
                    .dataFetcher(
                        "closePrice",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<AssetDailyPriceDTO>()?.closePrice
                        },
                    )
                    .dataFetcher(
                        "avgPrice",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<AssetDailyPriceDTO>()?.avgPrice
                        },
                    )
                    .dataFetcher(
                        "volume",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<AssetDailyPriceDTO>()?.volume
                        },
                    )
                    .dataFetcher(
                        "trades",
                        createValidatedDataFetcher {
                                env: DataFetchingEnvironment ->
                            env.getSource<AssetDailyPriceDTO>()?.trades
                        },
                    )
            }
    }
}
