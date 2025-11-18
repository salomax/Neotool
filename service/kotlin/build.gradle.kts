
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("io.micronaut.application") version "4.5.4" apply false
    id("io.micronaut.aot") version "4.5.4" apply false
    id("io.micronaut.library") version "4.5.4" apply false
    id("org.jetbrains.kotlin.plugin.jpa") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.3" apply false
    id("com.gradleup.shadow") version "8.3.7" apply false
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

// Apply JaCoCo plugin at root level for aggregated reports
apply(plugin = "jacoco")

// JaCoCo configuration for all subprojects
subprojects {
    apply(plugin = "jacoco")
    
    // Configure JaCoCo after plugin is applied
    afterEvaluate {
        // Configure JaCoCo
        tasks.withType<Test> {
            // Enable JaCoCo execution data collection
            finalizedBy(tasks.named("jacocoTestReport"))
        }
        
        tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport") {
            dependsOn(tasks.named("test"))
            reports {
                xml.required.set(true)
                html.required.set(true)
                csv.required.set(true)
            }
            
            // Exclude generated code, DTOs, entities, and test utilities from coverage
            classDirectories.setFrom(
                files(classDirectories.files.map {
                    fileTree(it) {
                        exclude(
                            "**/dto/**",
                            "**/entity/**",
                            "**/model/**",
                            "**/Application.class",
                            "**/ApplicationKt.class",
                            "**/*Mapper*.class",
                            "**/*Factory*.class",
                            "**/*Config*.class",
                            "**/test/**",
                            "**/common/test/**"
                        )
                    }
                })
            )
            
            // Print report path after generation
            doLast {
                val htmlReport = reports.html.outputLocation.asFile.get()
                println("\n" + "=".repeat(80))
                println("📊 JaCoCo Coverage Report Generated")
                println("=".repeat(80))
                println("📁 HTML Report: file://${htmlReport.absolutePath}/index.html")
                println("📄 XML Report:  ${reports.xml.outputLocation.asFile.get().absolutePath}")
                println("📊 CSV Report:  ${reports.csv.outputLocation.asFile.get().absolutePath}")
                println("=".repeat(80))
                println("💡 Open the HTML report in your browser to view detailed coverage")
                println("=".repeat(80) + "\n")
            }
        }
        
        // Coverage verification for unit tests (90% threshold for lines, instructions, branches)
        val coverageVerificationTask = tasks.maybeCreate("jacocoTestCoverageVerification", org.gradle.testing.jacoco.tasks.JacocoCoverageVerification::class.java)
        coverageVerificationTask.apply {
            val testReport = tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport")
            dependsOn(testReport)
            violationRules {
                rule {
                    // Overall coverage: 90% for lines, instructions, and branches
                    limit {
                        minimum = "0.90".toBigDecimal()
                    }
                    // Branch coverage is critical for testing all if/when/switch paths
                    limit {
                        counter = "BRANCH"
                        minimum = "0.85".toBigDecimal() // 85% branch coverage (slightly lower than line coverage)
                    }
                }
                // 100% coverage required for all service packages (including branches)
                // This ensures all business logic services are fully tested
                rule {
                    element = "PACKAGE"
                    includes = listOf(
                        "io.github.salomax.neotool.*.service.*",
                    )
                    limit {
                        minimum = "1.0".toBigDecimal()
                    }
                    limit {
                        counter = "BRANCH"
                        minimum = "1.0".toBigDecimal()
                    }
                }
            }
            classDirectories.setFrom(testReport.get().classDirectories)
            executionData.setFrom(testReport.get().executionData)
        }
        
        // Incremental coverage check for PRs (only checks changed lines)
        tasks.register<Exec>("jacocoIncrementalCoverageCheck") {
            group = "verification"
            description = "Checks coverage only for lines changed in PR (incremental coverage)"
            
            val testReport = tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport")
            dependsOn(testReport)
            
            val moduleName = project.name
            val scriptPath = rootProject.file("service/kotlin/scripts/check-incremental-coverage.sh")
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
        
        // Integration test coverage configuration (only for modules with testIntegration task)
        // Use whenTaskAdded to handle tasks registered after this block, and also check if already exists
        var integrationCoverageConfigured = false
        
        fun configureIntegrationTestCoverage() {
            if (integrationCoverageConfigured) return
            integrationCoverageConfigured = true
            // JaCoCo report for integration tests
            tasks.register<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoIntegrationTestReport") {
                dependsOn(tasks.named("testIntegration"))
                executionData(tasks.named("testIntegration"))
                val sourceSets = project.extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()
                sourceSets(sourceSets.getByName("main"))
                
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                    csv.required.set(true)
                }
                
                // Exclude generated code, DTOs, entities from coverage
                classDirectories.setFrom(
                    files(classDirectories.files.map {
                        fileTree(it) {
                            exclude(
                                "**/dto/**",
                                "**/entity/**",
                                "**/model/**",
                                "**/Application.class",
                                "**/ApplicationKt.class",
                                "**/*Mapper*.class",
                                "**/*Factory*.class",
                                "**/*Config*.class",
                                "**/test/**"
                            )
                        }
                    })
                )
                
                // Print report path after generation
                doLast {
                    val htmlReport = reports.html.outputLocation.asFile.get()
                    println("\n" + "=".repeat(80))
                    println("📊 JaCoCo Integration Test Coverage Report Generated")
                    println("=".repeat(80))
                    println("📁 HTML Report: file://${htmlReport.absolutePath}/index.html")
                    println("📄 XML Report:  ${reports.xml.outputLocation.asFile.get().absolutePath}")
                    println("📊 CSV Report:  ${reports.csv.outputLocation.asFile.get().absolutePath}")
                    println("=".repeat(80))
                    println("💡 Open the HTML report in your browser to view detailed coverage")
                    println("=".repeat(80) + "\n")
                }
            }
            
            // Configure testIntegration to finalize with coverage report
            tasks.named("testIntegration") {
                finalizedBy(tasks.named("jacocoIntegrationTestReport"))
            }
            
            // Coverage verification for integration tests (80% threshold for lines/instructions, 75% for branches)
            tasks.register<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoIntegrationTestCoverageVerification") {
                val integrationReport = tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoIntegrationTestReport")
                dependsOn(integrationReport)
                violationRules {
                    rule {
                        limit {
                            minimum = "0.80".toBigDecimal()
                        }
                        // Branch coverage for integration tests (slightly lower threshold)
                        limit {
                            counter = "BRANCH"
                            minimum = "0.75".toBigDecimal()
                        }
                    }
                    // 100% coverage required for all service packages in integration tests too
                    rule {
                        element = "PACKAGE"
                        includes = listOf(
                            "io.github.salomax.neotool.security.service.*",
                            "io.github.salomax.neotool.*.service.*",
                            "io.github.salomax.procureflow.*.service.*"
                        )
                        limit {
                            minimum = "1.0".toBigDecimal()
                        }
                        limit {
                            counter = "BRANCH"
                            minimum = "1.0".toBigDecimal()
                        }
                    }
                }
                classDirectories.setFrom(integrationReport.get().classDirectories)
                executionData.setFrom(integrationReport.get().executionData)
            }
        }
        
        // Check if testIntegration task already exists
        if (tasks.names.contains("testIntegration")) {
            configureIntegrationTestCoverage()
        }
        
        // Also handle case where task is registered after this block
        tasks.all {
            if (name == "testIntegration" && !integrationCoverageConfigured) {
                configureIntegrationTestCoverage()
            }
        }
    }
}

// Aggregate coverage report across all modules
gradle.projectsEvaluated {
    tasks.register<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoRootReport") {
        dependsOn(subprojects.map { it.tasks.named("jacocoTestReport") })
        
        subprojects.forEach { subproject ->
            val sourceSets = subproject.extensions.findByType<org.gradle.api.tasks.SourceSetContainer>()
            if (sourceSets != null) {
                val mainSourceSet = sourceSets.getByName("main")
                additionalSourceDirs.from(mainSourceSet.allSource.srcDirs)
                sourceDirectories.from(mainSourceSet.allSource.srcDirs)
                classDirectories.from(mainSourceSet.output)
            }
        }
        
        executionData.setFrom(subprojects.map { fileTree(it.layout.buildDirectory).include("**/jacoco/*.exec") })
        
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(true)
            html.outputLocation.set(file("${layout.buildDirectory.get()}/reports/jacoco/root/html"))
        }
        
        // Print report path after generation
        doLast {
            val htmlReport = reports.html.outputLocation.asFile.get()
            println("\n" + "=".repeat(80))
            println("📊 JaCoCo Aggregated Coverage Report Generated (All Modules)")
            println("=".repeat(80))
            println("📁 HTML Report: file://${htmlReport.absolutePath}/index.html")
            println("📄 XML Report:  ${reports.xml.outputLocation.asFile.get().absolutePath}")
            println("📊 CSV Report:  ${reports.csv.outputLocation.asFile.get().absolutePath}")
            println("=".repeat(80))
            println("💡 Open the HTML report in your browser to view detailed coverage")
            println("=".repeat(80) + "\n")
        }
    }
}
