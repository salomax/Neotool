plugins {
    id("dev.nx.gradle.project-graph") version "0.1.12" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.allopen") version "2.2.20" apply false
    id("io.micronaut.application") version "4.5.4" apply false
    id("io.micronaut.aot") version "4.5.4" apply false
    id("io.micronaut.library") version "4.5.4" apply false
    id("org.jetbrains.kotlin.plugin.jpa") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.3" apply false
    id("com.gradleup.shadow") version "8.3.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.3" apply false
}

val devpodBuildRoot: File? =
    if (System.getenv("DEVPOD") == "true") {
        val workspaceUid = System.getenv("DEVPOD_WORKSPACE_UID") ?: "default"
        val configuredRoot = System.getenv("NEOTOOL_GRADLE_BUILD_ROOT")
        file(configuredRoot ?: "${System.getProperty("user.home")}/.cache/neotool-gradle/${workspaceUid}/builds")
    } else {
        null
    }

fun Project.devpodBuildDir(baseDir: File): File {
    val projectKey = if (path == ":") "root" else path.removePrefix(":").replace(':', '/')
    return File(baseDir, projectKey)
}

allprojects {
    devpodBuildRoot?.let { root ->
        layout.buildDirectory.set(layout.dir(providers.provider { devpodBuildDir(root) }))
    }

    // Exclude root project to avoid Nx "Unable to parse dependency" errors - root project
    // has no Nx project node, so root->subproject dependencies cannot be resolved
    if (project != rootProject && project.hasProperty("workspaceRoot")) {
        apply(plugin = "dev.nx.gradle.project-graph")
    }
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
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
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
        systemProperty("api.version", "1.41")

        // Mockito and MockK both need JVM instrumentation via ByteBuddy.
        // On macOS self-attach usually works; in Linux containers (DevPod) it
        // often fails with "Could not self-attach to current VM".
        //
        // Attach ONLY byte-buddy-agent (not also mockito-core):
        // - MockK looks up Instrumentation via ByteBuddyAgent.install()
        // - Mockito reuses that same Instrumentation for the inline mock-maker
        // Attaching both agents causes retransform conflicts like:
        // "class redefinition failed: attempted to delete a method"
        // See: https://github.com/mockk/mockk/issues/1171
        // See: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html#0.3
        jvmArgumentProviders.add(
            CommandLineArgumentProvider {
                val byteBuddyAgent =
                    classpath.files.firstOrNull {
                        it.name.startsWith("byte-buddy-agent-") && it.name.endsWith(".jar")
                    }
                if (byteBuddyAgent != null) {
                    listOf("-javaagent:${byteBuddyAgent.absolutePath}")
                } else {
                    emptyList()
                }
            },
        )
    }
}

// Apply Kover plugin at root level for aggregated reports
apply(plugin = "org.jetbrains.kotlinx.kover")

// Kover configuration for all subprojects
subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.kotlin.plugin.allopen")
        extensions.configure<org.jetbrains.kotlin.allopen.gradle.AllOpenExtension> {
            annotation("io.github.salomax.neotool.common.tracing.TraceExecution")
            annotation("io.github.salomax.neotool.security.http.RequiresAuthorization")
        }
    }

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

    // Mark all Kover tasks as incompatible with configuration cache.
    // Kover modifies test JVM arguments (adds -javaagent) which are not correctly
    // replayed by the configuration cache, causing test failures.
    // Use tasks.all to catch tasks as they're created (before afterEvaluate)
    tasks.all {
        if (name.startsWith("kover")) {
            notCompatibleWithConfigurationCache("Kover tasks are not compatible with configuration cache")
        }
    }

    // Configure Kover after plugin is applied
    afterEvaluate {
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            // Kover's default instrumenter conflicts with Mockito/MockK ByteBuddy
            // inline mocks ("class redefinition failed: attempted to delete a method").
            // JaCoCo coexists with ByteBuddy agents; needed especially when we preload
            // byte-buddy-agent for DevPod (no self-attach).
            // See: https://github.com/Kotlin/kotlinx-kover/issues/742
            useJacoco()

            reports {
                filters {
                    excludes {
                        val classExclusions =
                            mutableListOf(
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
                            "*mapper.*",
                            "*Kt",
                            "io.github.salomax.neotool.comms.events.*",
                            "*DataLoader*",
                        )
                        if (project.path != ":banking") {
                            classExclusions += "*domain.*"
                        }
                        classes(*classExclusions.toTypedArray())
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
            tasks.findByName("test")?.let {
                dependsOn(it)
            }
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
