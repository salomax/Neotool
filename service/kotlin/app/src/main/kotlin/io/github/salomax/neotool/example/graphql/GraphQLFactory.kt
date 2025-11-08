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
        reps?.mapNotNull { rep ->
          val id = rep["id"]
          if (id == null) {
            null
          } else {
            try {
              when (rep["__typename"]) {
                "Product" -> productService.get(toUUID(id))
                "Customer" -> customerService.get(toUUID(id))
                else -> null
              }
            } catch (e: Exception) {
              // Log and return null if ID conversion fails
              val logger = org.slf4j.LoggerFactory.getLogger(GraphQLFactory::class.java)
              logger.debug("Failed to fetch entity for federation: ${rep["__typename"]} with id: $id", e)
              null
            }
          }
        }
      }
      .resolveEntityType { env ->
        val entity = env.getObject<Any?>()
        val schema = env.schema
        
        if (schema == null) {
          throw IllegalStateException("GraphQL schema is null in resolveEntityType")
        }

        when (entity) {
          is Product -> schema.getObjectType("Product")
            ?: throw IllegalStateException("Product type not found in schema")
          is Customer -> schema.getObjectType("Customer")
            ?: throw IllegalStateException("Customer type not found in schema")
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
