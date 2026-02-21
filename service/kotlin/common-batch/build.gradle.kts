plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.library")
    id("com.google.devtools.ksp")
}

repositories { mavenCentral() }

micronaut {
    version("4.10.6")
    processing {
        annotations("io.github.salomax.neotool.common.batch.*")
    }
}

dependencies {
    api(project(":common"))
    api("io.micronaut.kafka:micronaut-kafka")
    ksp("io.micronaut:micronaut-inject-kotlin")

    testImplementation("org.testcontainers:kafka")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
}
