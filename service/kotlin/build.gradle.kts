plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("io.micronaut.application") version "4.5.4" apply false
    id("io.micronaut.aot") version "4.5.4" apply false
    id("io.micronaut.library") version "4.5.4" apply false
    id("org.jetbrains.kotlin.plugin.jpa") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.3" apply false
    id("com.gradleup.shadow") version "8.3.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.3" apply false
}

allprojects {
    group = "io.github.salomax.neotool"

    repositories { 
        mavenCentral()
    }

    plugins.withId("io.micronaut.application") {
      the<io.micronaut.gradle.MicronautExtension>().processing {
        incremental(true)
        annotations("io.github.salomax.neotool.*")
      }
    }

    plugins.withId("io.micronaut.library") {
      the<io.micronaut.gradle.MicronautExtension>().processing {
        incremental(true)
        annotations("io.github.salomax.neotool.*")
      }
    }

  // Configure Kotlin compilation
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    
    // Configure Java compilation
    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    
    // Configure test tasks
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// Apply Kover plugin at root level for aggregated reports
apply(plugin = "org.jetbrains.kotlinx.kover")

// Kover configuration for all subprojects
subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    // Configure Kover at subproject level
    // Note: Verification rules with specific thresholds need to be configured
    // via koverVerify task or kover extension block after plugin evaluation
    
    // Configure ktlint
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.1.1")
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        filter {
            exclude("**/build/**")
            exclude("**/generated/**")
            exclude("**/.gradle/**")
        }
    }
    
    // Configure ktlint tasks
    tasks.named("ktlintCheck") {
        group = "verification"
        description = "Check Kotlin code style with ktlint"
    }
    
    tasks.named("ktlintFormat") {
        group = "formatting"
        description = "Format Kotlin code with ktlint"
    }
    
    // Make check task depend on ktlintCheck (if check task exists)
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(tasks.named("ktlintCheck"))
    }
    
    // Mark Kover HTML report tasks as incompatible with configuration cache
    // Use tasks.all to catch tasks as they're created (before afterEvaluate)
    tasks.all {
        if (name == "koverHtmlReport") {
            notCompatibleWithConfigurationCache("Kover HTML report tasks are not compatible with configuration cache")
        }
    }
    
    // Configure Kover after plugin is applied
    afterEvaluate {
        // Configure Kover to exclude Micronaut internals and generated classes
        // This prevents ClassFormatError while still collecting coverage for application code
        // Use extension by name with dynamic invocation to avoid type resolution issues
        extensions.findByName("kover")?.let { kover ->
            try {
                // Use dynamic method invocation to configure Kover
                // This avoids the need to resolve the KoverExtension type at configuration time
                val instrumentation = kover.javaClass.getMethod("getInstrumentation").invoke(kover)
                val excludedClasses = instrumentation.javaClass.getMethod("getExcludedClasses").invoke(instrumentation) as? MutableCollection<String>
                excludedClasses?.apply {
                    // Exclude Micronaut service loader classes that cause ClassFormatError
                    // These are loaded via service loading and instrumentation breaks them
                    add("io.micronaut.core.io.service.SoftServiceLoader")
                    add("io.micronaut.core.io.service.ServiceLoader")
                    // Exclude generated classes (KSP, annotation processors) using class name patterns
                    add("*\$*") // Generated inner classes (e.g., MyClass$Companion)
                    add("*Generated*") // Classes with "Generated" in name
                    add("*_Factory*") // KSP factory classes
                    add("*_Impl*") // KSP implementation classes
                    add("*_Builder*") // Builder classes
                    // Exclude test infrastructure classes from coverage
                    add("io.github.salomax.neotool.common.test.*")
                }
            } catch (e: Exception) {
                // If reflection fails, skip configuration (Kover will use defaults)
                logger.warn("Could not configure Kover exclusions: ${e.message}")
            }
        }
        
        // Configure test tasks to finalize with Kover reports
        // But disable Kover for integration tests to avoid ClassFormatError
        tasks.withType<Test> {
            // Disable Kover instrumentation for integration tests
            // This prevents ClassFormatError caused by instrumenting Micronaut service loader classes
            if (name == "testIntegration") {
                // Disable Kover for integration tests to prevent ClassFormatError
                // This prevents instrumentation of Micronaut service loader classes
                extensions.findByName("kover")?.let { koverExt ->
                    try {
                        // Access isDisabled property via reflection
                        val isDisabledMethod = koverExt.javaClass.getMethod("getIsDisabled")
                        val isDisabledProp = isDisabledMethod.invoke(koverExt)
                        if (isDisabledProp is org.gradle.api.provider.Property<*>) {
                            @Suppress("UNCHECKED_CAST")
                            (isDisabledProp as org.gradle.api.provider.Property<Boolean>).set(true)
                            logger.info("Kover disabled for testIntegration task in ${project.name}")
                        }
                    } catch (e: Exception) {
                        logger.warn("Could not disable Kover for testIntegration in ${project.name}: ${e.message}")
                    }
                }
            } else {
                // Only generate coverage reports for unit tests
                finalizedBy(tasks.named("koverXmlReport"))
                finalizedBy(tasks.named("koverHtmlReport"))
            }
        }
        
        // Configure koverVerify task with coverage thresholds
        tasks.named("koverVerify") {
            // Verification rules are configured via the kover extension
            // For now, we'll rely on default Kover behavior
            // TODO: Add specific verification rules once API is confirmed
        }
        
        // Note: Custom doLast block removed to avoid configuration cache issues
        // Kover already prints the report location automatically
        
        // Incremental coverage check for PRs (only checks changed lines)
        tasks.register<Exec>("koverIncrementalCoverageCheck") {
            group = "verification"
            description = "Checks coverage only for lines changed in PR (incremental coverage)"
            
            dependsOn(tasks.named("koverXmlReport"))
            
            val moduleName = project.name
            val scriptPath = rootProject.file("scripts/check-incremental-coverage.sh")
            val baseBranch = project.findProperty("coverage.baseBranch") as String? ?: "main"
            val threshold = project.findProperty("coverage.incrementalThreshold") as String? ?: "80"
            
            commandLine("bash", scriptPath.absolutePath, moduleName, baseBranch)
            
            environment("COVERAGE_THRESHOLD", threshold)
            environment("BASE_BRANCH", baseBranch)
            
            // Only fail if script fails (non-zero exit code)
            isIgnoreExitValue = false
            
            doFirst {
                if (!scriptPath.exists()) {
                    throw GradleException("Incremental coverage script not found at: ${scriptPath.absolutePath}")
                }
                if (!scriptPath.canExecute()) {
                    scriptPath.setExecutable(true)
                }
            }
        }
        
        // Integration tests have Kover disabled to prevent ClassFormatError
        // No need to generate coverage reports for integration tests
        // Unit test coverage is sufficient for code coverage metrics
        
        // Combined coverage report task (unit tests + integration tests)
        // Kover automatically combines coverage from all test tasks
        tasks.register("koverCombinedCoverageReport") {
            group = "verification"
            description = "Generates combined coverage report (Unit + Integration Tests)"
            dependsOn(tasks.named("test"))
            if (tasks.names.contains("testIntegration")) {
                dependsOn(tasks.named("testIntegration"))
            }
            dependsOn(tasks.named("koverXmlReport"))
            dependsOn(tasks.named("koverHtmlReport"))
            
            // Note: Custom doLast block removed to avoid configuration cache issues
            // Kover already prints the report location automatically
        }
    }
}

// Aggregate coverage report across all modules
gradle.projectsEvaluated {
    tasks.register("koverRootReport") {
        group = "verification"
        description = "Generates aggregated coverage report across all modules"
        dependsOn(subprojects.map { it.tasks.named("koverXmlReport") })
        dependsOn(subprojects.map { it.tasks.named("koverHtmlReport") })
        
        // Print report path after generation
        doLast {
            println("\n" + "=".repeat(80))
            println("📊 Kover Aggregated Coverage Report Generated (All Modules)")
            println("=".repeat(80))
            println("📁 HTML Reports: Check individual module reports in build/reports/kover/html/")
            println("📄 XML Reports:  Check individual module reports in build/reports/kover/xml/")
            println("=".repeat(80))
            println("💡 Open the HTML reports in your browser to view detailed coverage")
            println("=".repeat(80) + "\n")
        }
    }
    
    // Root-level ktlint tasks that run across all subprojects
    tasks.register("ktlintCheck") {
        group = "verification"
        description = "Run ktlint check on all subprojects"
        dependsOn(subprojects.map { it.tasks.named("ktlintCheck") })
    }
    
    tasks.register("ktlintFormat") {
        group = "formatting"
        description = "Format Kotlin code with ktlint on all subprojects"
        dependsOn(subprojects.map { it.tasks.named("ktlintFormat") })
    }
}

