# Start by using the official Gradle image with JDK 21 as our builder stage.
FROM gradle:8.10.2-jdk21 AS build

# Set the working directory inside the container for the build process.
WORKDIR /home/gradle/project

# Copy Gradle wrapper and build files first for better layer caching
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts gradle.properties ./
# Use Docker-only settings that include only the modules we copy
COPY financialdata/settings.docker.gradle.kts ./settings.gradle.kts

# Copy common module first (shared dependency)
COPY common/build.gradle.kts ./common/
COPY common/src ./common/src

# Shared finance utilities (tax rules, etc.)
COPY common-finance/build.gradle.kts ./common-finance/
COPY common-finance/src ./common-finance/src

# Copy financialdata module build files
COPY financialdata/build.gradle.kts ./financialdata/
COPY financialdata/src ./financialdata/src

# Download dependencies and build common module first (shared by all services)
# This maximizes cache reuse across all service builds
# Using BuildKit cache mount to share Gradle cache between builds
# Using :common:assemble instead of :common:build to skip verification tasks (koverVerify, check)
# since we're skipping tests and only need the JAR artifact
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    ./gradlew :common:assemble :common-finance:assemble --build-cache

# Build the application JAR
# - './gradlew :financialdata:shadowJar': Executes the `shadowJar` task on the `:financialdata` module. This task typically
#   creates an "uber" or "fat" JAR containing the application code and all its dependencies.
# - '-x test': Skips running any tests during the build.
# - '--build-cache': Uses the Gradle build cache for faster incremental builds
# - '--parallel': Builds modules in parallel for better performance
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    ./gradlew :financialdata:shadowJar -x test --build-cache --parallel

# Use a minimal JRE (Java Runtime Environment) image based on Eclipse Temurin with JDK 21.
# This image is much smaller than the full JDK image used for building.
FROM eclipse-temurin:21-jre-alpine

# Set the working directory for the application inside the final image.
WORKDIR /financialdata

# Copy the generated "fat JAR" from the 'build' stage to the final image's working directory.
# - 'COPY --from=build': Specifies that the file should be copied from the stage named 'build'.
# - '/home/gradle/project/financialdata/build/libs/financialdata-all.jar': The path to the built JAR file in the 'build' container.
# - '/financialdata/service.jar': The destination path and name in the final container.
COPY --from=build /home/gradle/project/financialdata/build/libs/financialdata-all.jar /financialdata/service.jar

# Inform Docker that the container will listen on the specified port at runtime.
# This is documentation and does not actually publish the port; it's a metadata instruction.
EXPOSE 8080

# Define the command that runs when the container starts.
# - '["java","-jar","/app/service.jar"]': Executes the JAR file using the Java Runtime.
ENTRYPOINT ["java","-jar","/financialdata/service.jar"]

