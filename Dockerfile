# syntax=docker/dockerfile:1

# ---- Build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so they are cached between source changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon --quiet bootJar

# ---- Run ----
FROM eclipse-temurin:21-jre
RUN groupadd --system memolake && useradd --system --gid memolake --home-dir /app memolake
WORKDIR /app
COPY --from=build /workspace/build/libs/memolake.jar memolake.jar
USER memolake

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/memolake.jar"]
