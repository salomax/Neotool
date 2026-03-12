package io.github.salomax.neotool.common.graphql.scalar

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.scalars.ExtendedScalars
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Backward-compatible DateTime scalar.
 *
 * GraphQL Java extended DateTime expects OffsetDateTime by default.
 * Many domain models still expose Instant, so we normalize Instant -> OffsetDateTime(UTC)
 * before delegating to the standard DateTime scalar.
 */
object NeotoolDateTimeScalar {
    private val delegate = ExtendedScalars.DateTime.coercing

    val dateTime: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("DateTime")
            .description("ISO-8601 DateTime scalar that accepts OffsetDateTime and Instant")
            .coercing(
                object : Coercing<Any, Any> {
                    override fun serialize(
                        dataFetcherResult: Any,
                        graphQLContext: GraphQLContext,
                        locale: Locale,
                    ): Any {
                        val normalized =
                            when (dataFetcherResult) {
                                is Instant -> dataFetcherResult.atOffset(ZoneOffset.UTC)
                                else -> dataFetcherResult
                            }

                        return try {
                            delegate.serialize(normalized, graphQLContext, locale)
                                ?: throw CoercingSerializeException("DateTime serialization returned null")
                        } catch (e: RuntimeException) {
                            throw CoercingSerializeException("Invalid DateTime value: ${e.message}", e)
                        }
                    }

                    override fun parseValue(
                        input: Any,
                        graphQLContext: GraphQLContext,
                        locale: Locale,
                    ): Any {
                        return try {
                            when (input) {
                                is Instant -> input.atOffset(ZoneOffset.UTC)
                                is OffsetDateTime -> input
                                is String -> OffsetDateTime.parse(input)
                                else ->
                                    delegate.parseValue(input, graphQLContext, locale)
                                        ?: throw CoercingParseValueException("DateTime parseValue returned null")
                            }
                        } catch (e: Exception) {
                            throw CoercingParseValueException("Invalid DateTime input: ${e.message}", e)
                        }
                    }

                    override fun parseLiteral(
                        input: Value<*>,
                        variables: CoercedVariables,
                        graphQLContext: GraphQLContext,
                        locale: Locale,
                    ): Any {
                        return try {
                            when (input) {
                                is StringValue -> OffsetDateTime.parse(input.value)
                                else ->
                                    delegate.parseLiteral(input, variables, graphQLContext, locale)
                                        ?: throw CoercingParseLiteralException("DateTime parseLiteral returned null")
                            }
                        } catch (e: Exception) {
                            throw CoercingParseLiteralException("Invalid DateTime literal: ${e.message}", e)
                        }
                    }
                },
            ).build()
}
