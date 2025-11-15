# Start by using the official Gradle image with JDK 21 as our builder stage.
FROM gradle:8.10.2-jdk21 AS build

# Set the working directory inside the container for the build process.
WORKDIR /home/gradle/project

# Copy the entire build context (which is the root of your Gradle project, e.g., 'service/')
# into the container's working directory.
COPY . /home/gradle/project

# Run the Gradle command to build the application.
# - './gradlew :app:shadowJar': Executes the `shadowJar` task on the `:app` module. This task typically
#   creates an "uber" or "fat" JAR containing the application code and all its dependencies.
# - '-x test': Skips running any tests during the build.
# - '--no-daemon': Ensures the Gradle daemon is not used, which is generally better for single-run Docker builds
#   as it prevents a long-running process from persisting unnecessarily.
# - '-Dkotlin.compiler.execution.strategy=in-process': Forces the Kotlin compiler to run in the same process,
#   reducing overhead when compared to using a separate daemon.
RUN ./gradlew :app:shadowJar -x test --no-daemon \
    -Dkotlin.compiler.execution.strategy=in-process

# Use a minimal JRE (Java Runtime Environment) image based on Eclipse Temurin with JDK 21.
# This image is much smaller than the full JDK image used for building.
FROM eclipse-temurin:21-jre

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
