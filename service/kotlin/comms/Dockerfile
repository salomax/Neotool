# Start by using the official Gradle image with JDK 21 as our builder stage.
FROM gradle:8.10.2-jdk21 AS build

# Set the working directory inside the container for the build process.
WORKDIR /home/gradle/project

# Copy Gradle wrapper and build files first for better layer caching
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# Copy common module first (shared dependency)
COPY common/build.gradle.kts ./common/
COPY common/src ./common/src
# Common module for batch (Kafka consumer)
COPY common-batch/build.gradle.kts ./common-batch/
COPY common-batch/src ./common-batch/src

# Copy comms module build files
COPY comms/build.gradle.kts ./comms/
COPY comms/src ./comms/src

# Download dependencies and build common modules first (shared by comms)
# This maximizes cache reuse across all service builds
# Using BuildKit cache mount to share Gradle cache between builds
# Using :common:assemble instead of :common:build to skip verification tasks (koverVerify, check)
# since we're skipping tests and only need the JAR artifact
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    ./gradlew :common:assemble :common-batch:assemble --build-cache

# Build the application JAR
# - './gradlew :comms:shadowJar': Executes the `shadowJar` task on the `:comms` module. This task typically
#   creates an "uber" or "fat" JAR containing the application code and all its dependencies.
# - '-x test': Skips running any tests during the build.
# - '--build-cache': Uses the Gradle build cache for faster incremental builds
# - '--parallel': Builds modules in parallel for better performance
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    ./gradlew :comms:shadowJar -x test --build-cache --parallel

# Use a minimal JRE (Java Runtime Environment) image based on Eclipse Temurin with JDK 21.
# This image is much smaller than the full JDK image used for building.
FROM eclipse-temurin:21-jre-alpine

# Set the working directory for the application inside the final image.
WORKDIR /comms

# Copy the generated "fat JAR" from the 'build' stage to the final image's working directory.
COPY --from=build /home/gradle/project/comms/build/libs/comms-all.jar /comms/service.jar

# Inform Docker that the container will listen on the specified port at runtime.
# This is documentation and does not actually publish the port; it's a metadata instruction.
EXPOSE 8080

# Define the command that runs when the container starts.
ENTRYPOINT ["java","-jar","/comms/service.jar"]
