# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:26-jdk-alpine AS build

WORKDIR /workspace

COPY pom.xml .
COPY src src

RUN apk add --no-cache maven && \
    mvn -q -B package -DskipTests

# ── Stage 2: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:26-jre-alpine

RUN addgroup -S tasktracker && adduser -S tasktracker -G tasktracker
USER tasktracker

WORKDIR /app

COPY --from=build /workspace/target/task-tracker-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
