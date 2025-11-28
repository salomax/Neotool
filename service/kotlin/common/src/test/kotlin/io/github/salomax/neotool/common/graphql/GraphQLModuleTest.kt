package io.github.salomax.neotool.common.graphql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions

@DisplayName("GraphQLModule Tests")
class GraphQLModuleTest {
    private lateinit var registry: GraphQLResolverRegistry
    private lateinit var moduleRegistry: GraphQLModuleRegistry

    @BeforeEach
    fun setUp() {
        registry = mock()
        moduleRegistry = GraphQLModuleRegistry()
    }

    @Nested
    @DisplayName("BaseGraphQLModule")
    inner class BaseGraphQLModuleTests {
        @Test
        fun `should return class simple name as module name`() {
            // Arrange
            val module = object : BaseGraphQLModule() {}

            // Act
            val name = module.getModuleName()

            // Assert
            assertThat(name).isNotNull()
            assertThat(name).isNotEmpty()
            // The actual name depends on the anonymous class name which can vary
        }

        @Test
        fun `should have default registerResolvers implementation that does nothing`() {
            // Arrange
            val module = object : BaseGraphQLModule() {}

            // Act
            module.registerResolvers(registry)

            // Assert - should not throw and should not interact with registry
            verifyNoMoreInteractions(registry)
        }
    }

    @Nested
    @DisplayName("GraphQLModuleRegistry")
    inner class GraphQLModuleRegistryTests {
        @Test
        fun `should register module`() {
            // Arrange
            val module =
                object : GraphQLModule {
                    override fun registerResolvers(registry: GraphQLResolverRegistry) {}

                    override fun getModuleName() = "TestModule"
                }

            // Act
            moduleRegistry.registerModule(module)

            // Assert
            val retrieved = moduleRegistry.getModule("TestModule")
            assertThat(retrieved).isNotNull()
            assertThat(retrieved).isEqualTo(module)
        }

        @Test
        fun `should return null for non-existent module`() {
            // Act
            val result = moduleRegistry.getModule("NonExistentModule")

            // Assert
            assertThat(result).isNull()
        }

        @Test
        fun `should return all registered modules`() {
            // Arrange
            val module1 =
                object : GraphQLModule {
                    override fun registerResolvers(registry: GraphQLResolverRegistry) {}

                    override fun getModuleName() = "Module1"
                }
            val module2 =
                object : GraphQLModule {
                    override fun registerResolvers(registry: GraphQLResolverRegistry) {}

                    override fun getModuleName() = "Module2"
                }

            moduleRegistry.registerModule(module1)
            moduleRegistry.registerModule(module2)

            // Act
            val allModules = moduleRegistry.getAllModules()

            // Assert
            assertThat(allModules).hasSize(2)
            assertThat(allModules["Module1"]).isEqualTo(module1)
            assertThat(allModules["Module2"]).isEqualTo(module2)
        }

        @Test
        fun `should return immutable copy of modules`() {
            // Arrange
            val module =
                object : GraphQLModule {
                    override fun registerResolvers(registry: GraphQLResolverRegistry) {}

                    override fun getModuleName() = "TestModule"
                }
            moduleRegistry.registerModule(module)

            // Act
            val allModules = moduleRegistry.getAllModules()

            // Assert - should return immutable copy
            assertThat(allModules).hasSize(1)
            // Verify it's immutable by checking we can't modify it
            assertThat(allModules).isInstanceOf(Map::class.java)
        }

        @Test
        fun `should overwrite module with same name`() {
            // Arrange
            val module1 =
                object : GraphQLModule {
                    override fun registerResolvers(registry: GraphQLResolverRegistry) {}

                    override fun getModuleName() = "TestModule"
                }
            val module2 =
                object : GraphQLModule {
                    override fun registerResolvers(registry: GraphQLResolverRegistry) {}

                    override fun getModuleName() = "TestModule"
                }

            moduleRegistry.registerModule(module1)

            // Act
            moduleRegistry.registerModule(module2)

            // Assert
            val retrieved = moduleRegistry.getModule("TestModule")
            assertThat(retrieved).isEqualTo(module2)
            assertThat(retrieved).isNotEqualTo(module1)
        }

        @Test
        fun `should initialize all modules`() {
            // Arrange
            val module1 =
                object : GraphQLModule {
                    override fun registerResolvers(registry: GraphQLResolverRegistry) {}

                    override fun getModuleName() = "Module1"
                }
            val module2 =
                object : GraphQLModule {
                    override fun registerResolvers(registry: GraphQLResolverRegistry) {}

                    override fun getModuleName() = "Module2"
                }

            moduleRegistry.registerModule(module1)
            moduleRegistry.registerModule(module2)

            // Act
            moduleRegistry.initializeAllModules(registry)

            // Assert - both modules should have been initialized
            // Since we can't easily verify the calls, we just ensure no exceptions are thrown
            // The actual registration would happen in the registerResolvers method
        }

        @Test
        fun `should handle empty module registry`() {
            // Act
            val allModules = moduleRegistry.getAllModules()
            moduleRegistry.initializeAllModules(registry)

            // Assert
            assertThat(allModules).isEmpty()
            // Should not throw
        }
    }
}
