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

    // Configure ktlint tasks and check dependencies after plugin evaluation
    afterEvaluate {
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
        // and set coverage thresholds aligned with big tech standards
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            reports {
                filters {
                    excludes {
                        classes(
                            "io.micronaut.core.io.service.SoftServiceLoader",
                            "io.micronaut.core.io.service.ServiceLoader",
                            "*Application",
                            "*\$*", // Generated inner classes
                            "*Generated*",
                            "*Factory*",
                            "*Builder*",
                            "*Entity",
                            "*Helper*",
                            "*Mapper*",
                            "*Utils*",
                            "*Test",
                            "*DTO",
                            "*Config*",
                            "*domain.*",
                            "*mapper.*",
                        )
                    }
                }

                // Coverage thresholds based on industry standards:
                verify {
                    // Overall minimum coverage threshold (applies to all counter types)
                    rule {
                        minBound(80)
                    }
                }
            }
        }

        // Configure Kover report tasks
        // Reports are only generated when explicitly requested on the command line
        // This prevents all tests from running when running a single test
        tasks.named("koverXmlReport") {
            group = "verification"
            description =
                "Generates XML coverage report (run explicitly: ./gradlew test testIntegration koverXmlReport)"
            // Include testIntegration if it exists to merge coverage from integration tests
            tasks.findByName("testIntegration")?.let {
                dependsOn(it)
            }
        }
        tasks.named("koverHtmlReport") {
            group = "verification"
            description =
                "Generates HTML coverage report (run explicitly: ./gradlew test testIntegration koverHtmlReport)"
            // Include testIntegration if it exists to merge coverage from integration tests
            tasks.findByName("testIntegration")?.let {
                dependsOn(it)
            }
        }

        // Configure koverVerify task
        // Verification is only run when explicitly requested on the command line
        // This prevents all tests from running when running a single test
        tasks.named("koverVerify") {
            group = "verification"
            description =
                "Verifies that code coverage meets the minimum thresholds (run explicitly: ./gradlew test testIntegration koverVerify)"
            // Ensure both test and testIntegration run before verification
            // This merges coverage from both unit and integration tests
            dependsOn(tasks.named("test"))
            // Include testIntegration if it exists to merge coverage from integration tests
            tasks.findByName("testIntegration")?.let {
                dependsOn(it)
            }
        }

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
    }
}

// Aggregate coverage report across all modules
gradle.projectsEvaluated {
    tasks.register("koverRootReport") {
        group = "verification"
        description = "Generates aggregated coverage report across all modules"
        // Use findByName for safety in case a subproject doesn't have Kover tasks
        dependsOn(subprojects.mapNotNull { it.tasks.findByName("koverXmlReport") })
        dependsOn(subprojects.mapNotNull { it.tasks.findByName("koverHtmlReport") })

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
        dependsOn(subprojects.mapNotNull { it.tasks.findByName("ktlintCheck") })
    }

    tasks.register("ktlintFormat") {
        group = "formatting"
        description = "Format Kotlin code with ktlint on all subprojects"
        dependsOn(subprojects.mapNotNull { it.tasks.findByName("ktlintFormat") })
    }
}
