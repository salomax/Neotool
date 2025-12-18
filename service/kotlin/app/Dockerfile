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

# Copy app module build files
COPY app/build.gradle.kts ./app/
COPY app/src ./app/src

# Download dependencies and build common module first (shared by all services)
# This maximizes cache reuse across all service builds
# Using BuildKit cache mount to share Gradle cache between builds
# Using :common:assemble instead of :common:build to skip verification tasks (koverVerify, check)
# since we're skipping tests and only need the JAR artifact
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    ./gradlew :common:assemble --build-cache

# Build the application JAR
# - './gradlew :app:shadowJar': Executes the `shadowJar` task on the `:app` module. This task typically
#   creates an "uber" or "fat" JAR containing the application code and all its dependencies.
# - '-x test': Skips running any tests during the build.
# - '--build-cache': Uses the Gradle build cache for faster incremental builds
# - '--parallel': Builds modules in parallel for better performance
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    ./gradlew :app:shadowJar -x test --build-cache --parallel

# Use Alpine-based JRE (Java Runtime Environment) image - much smaller than standard JRE (~100MB vs ~200MB).
# Alpine Linux uses musl libc instead of glibc, resulting in a significantly smaller base image.
FROM eclipse-temurin:21-jre-alpine

# Set the working directory for the application inside the final image.
WORKDIR /app

# Copy the generated "fat JAR" from the 'build' stage to the final image's working directory.
# - 'COPY --from=build': Specifies that the file should be copied from the stage named 'build'.
# - '/home/gradle/project/app/build/libs/app-all.jar': The path to the built JAR file in the 'build' container.
# - '/app/service.jar': The destination path and name in the final container.
COPY --from=build /home/gradle/project/app/build/libs/app-all.jar /app/service.jar

# Inform Docker that the container will listen on the specified port at runtime.
# This is documentation and does not actually publish the port; it's a metadata instruction.
EXPOSE 8080

# Define the command that runs when the container starts.
# - '["java","-jar","/app/service.jar"]': Executes the JAR file using the Java Runtime.
ENTRYPOINT ["java","-jar","/app/service.jar"]
    