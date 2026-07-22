# Start by using the official Gradle image with JDK 21 as our builder stage.
FROM gradle:8.10.2-jdk21 AS build

WORKDIR /home/gradle/project

COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

COPY common/build.gradle.kts ./common/
COPY common/src ./common/src

COPY mcp/build.gradle.kts ./mcp/
COPY mcp/src ./mcp/src

RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    ./gradlew :common:assemble --build-cache

RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper \
    ./gradlew :mcp:shadowJar -x test --build-cache --parallel

FROM eclipse-temurin:21-jre-alpine

WORKDIR /mcp

COPY --from=build /home/gradle/project/mcp/build/libs/mcp-all.jar /mcp/service.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/mcp/service.jar"]
