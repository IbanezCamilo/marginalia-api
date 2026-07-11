# syntax=docker/dockerfile:1

# ─── Stage 1: build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the wrapper + POM first so dependency resolution is cached independently
# of source changes. This layer is only rebuilt when pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

# Now copy sources and build the fat jar. Tests are skipped here because CI
# (.github/workflows/ci.yml) already runs `./mvnw test` on push/PR.
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ─── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=build /app/target/blog-literario-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Profile and all secrets are injected at runtime via env vars (Dokploy).
ENTRYPOINT ["java", "-Xmx512m", "-XX:MaxMetaspaceSize=128m", "-jar", "app.jar"]
