# Multi-stage build for gls-app-assembly (the api container).
#
# Build context: repo root, NOT ./backend/. The compose service for
# this Dockerfile uses `context: .` and `dockerfile: backend/Dockerfile`
# so the build can reach the `contracts/` tree, which gls-platform-audit's
# Maven build copies into its jar via a build-time resource at
# `../../contracts/audit/`. Without the wider context, that resource
# silently doesn't bundle and the deployed jar's runtime schema
# validator misbehaves.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Layer 1: build descriptors. Each module's pom is copied first so
# Maven can resolve the reactor's version graph without pulling source
# files. Cache hits here mean unrelated source changes don't re-resolve
# dependencies.
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/mvnw
COPY backend/pom.xml backend/pom.xml
COPY backend/bom/pom.xml backend/bom/pom.xml
COPY backend/contracts-smoke/pom.xml backend/contracts-smoke/pom.xml
COPY backend/gls-platform/pom.xml backend/gls-platform/pom.xml
COPY backend/gls-platform-audit/pom.xml backend/gls-platform-audit/pom.xml
COPY backend/gls-platform-config/pom.xml backend/gls-platform-config/pom.xml
COPY backend/gls-governance/pom.xml backend/gls-governance/pom.xml
COPY backend/gls-document/pom.xml backend/gls-document/pom.xml
COPY backend/gls-document-processing/pom.xml backend/gls-document-processing/pom.xml
COPY backend/gls-mcp-server/pom.xml backend/gls-mcp-server/pom.xml
COPY backend/gls-llm-orchestration/pom.xml backend/gls-llm-orchestration/pom.xml
COPY backend/gls-governance-enforcement/pom.xml backend/gls-governance-enforcement/pom.xml
COPY backend/gls-app-assembly/pom.xml backend/gls-app-assembly/pom.xml
COPY backend/gls-governance-hub/pom.xml backend/gls-governance-hub/pom.xml
COPY backend/gls-governance-hub-app/pom.xml backend/gls-governance-hub-app/pom.xml
COPY backend/gls-extraction-tika/pom.xml backend/gls-extraction-tika/pom.xml

WORKDIR /workspace/backend
RUN ./mvnw dependency:go-offline -DskipTests -B -pl gls-app-assembly -am || true

# Layer 2: source + contracts. The contracts directory is essential
# for gls-platform-audit's bundled schema and for any per-service
# OpenAPI generator runs.
WORKDIR /workspace
COPY backend backend
COPY contracts contracts

WORKDIR /workspace/backend
RUN ./mvnw -DskipTests package -pl gls-app-assembly -am -o \
    || ./mvnw -DskipTests package -pl gls-app-assembly -am

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/gls-app-assembly/target/*.jar /app/app.jar
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
