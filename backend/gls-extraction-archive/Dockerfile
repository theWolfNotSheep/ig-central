# Multi-stage build for gls-extraction-archive.
#
# Build context: repo root (NOT backend/). The compose entry uses
# `context: .` and `dockerfile: backend/gls-extraction-archive/Dockerfile`
# so the build can reach the `contracts/` tree, which gls-platform-audit's
# Maven build copies into its jar via a build-time resource directive
# at `../../contracts/audit/`. Without the wider context, that resource
# copy silently produces a jar without the bundled schema.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Layer 1: build descriptors. Same enumeration pattern as backend/Dockerfile.
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
COPY backend/gls-extraction-archive/pom.xml backend/gls-extraction-archive/pom.xml

WORKDIR /workspace/backend
RUN ./mvnw dependency:go-offline -DskipTests -B -pl gls-extraction-archive -am || true

# Layer 2: source + contracts.
WORKDIR /workspace
COPY backend backend
COPY contracts contracts

WORKDIR /workspace/backend
RUN ./mvnw -DskipTests package -pl gls-extraction-archive -am -o \
    || ./mvnw -DskipTests package -pl gls-extraction-archive -am

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/gls-extraction-archive/target/*.jar /app/app.jar
EXPOSE 8090
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -sf http://localhost:8090/actuator/health || exit 1
CMD ["java","-jar","/app/app.jar"]
