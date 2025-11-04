package io.github.salomax.neotool.example.graphql

import com.apollographql.federation.graphqljava.Federation
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.service.CustomerService
import io.github.salomax.neotool.example.service.ProductService
import io.github.salomax.neotool.exception.GraphQLOptimisticLockExceptionHandler
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import io.github.salomax.neotool.framework.util.toUUID
import io.github.salomax.neotool.example.graphql.AppWiringFactory

@Factory
class GraphQLFactory(
  private val registry: TypeDefinitionRegistry,
  private val wiringFactory: AppWiringFactory,
  private val productService: ProductService,
  private val customerService: CustomerService
) {
  @Singleton
  fun graphQL(): graphql.GraphQL {
    val runtimeWiring = wiringFactory.build()

    val federatedSchema = Federation.transform(registry, runtimeWiring)
      .fetchEntities { env ->
        val reps = env.getArgument<List<Map<String, Any>>>("representations")
        reps?.map { rep ->
          when (rep["__typename"]) {
                "Product" -> productService.get(toUUID(rep["id"]))
                "Customer" -> customerService.get(toUUID(rep["id"]))
                else -> null
          }
        }
      }
      .resolveEntityType { env ->
        val entity = env.getObject<Any?>()

        when (entity) {
          is Product -> env.schema.getObjectType("Product")
          is Customer -> env.schema.getObjectType("Customer")
          else -> throw IllegalStateException(
            "Unknown federated type for entity: ${entity?.javaClass?.name}"
          )
        }
      }
      .build()

    return graphql.GraphQL.newGraphQL(federatedSchema)
      .instrumentation(MaxQueryComplexityInstrumentation(100))
      .instrumentation(MaxQueryDepthInstrumentation(10))
      .defaultDataFetcherExceptionHandler(GraphQLOptimisticLockExceptionHandler())
      .build()
  }
}
