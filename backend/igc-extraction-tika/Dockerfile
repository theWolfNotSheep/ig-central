# Multi-stage build for igc-extraction-tika.
#
# Build context: repo root (NOT backend/). The compose placeholder for
# this service uses `context: .` and `dockerfile: backend/igc-extraction-tika/Dockerfile`
# so the build can reach the `contracts/` tree, which igc-platform-audit's
# Maven build copies into its jar via a build-time resource directive
# at `../../contracts/audit/`. Without the wider context, that resource
# copy silently produces a jar without the bundled schema.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Layer 1: build descriptors. Same enumeration pattern as backend/Dockerfile.
# Each module's pom is copied so Maven can resolve the reactor's version
# graph without pulling source. Cache hits here mean unrelated source
# changes don't re-resolve dependencies.
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/mvnw
COPY backend/pom.xml backend/pom.xml
COPY backend/bom/pom.xml backend/bom/pom.xml
COPY backend/contracts-smoke/pom.xml backend/contracts-smoke/pom.xml
COPY backend/igc-platform/pom.xml backend/igc-platform/pom.xml
COPY backend/igc-platform-audit/pom.xml backend/igc-platform-audit/pom.xml
COPY backend/igc-platform-config/pom.xml backend/igc-platform-config/pom.xml
COPY backend/igc-governance/pom.xml backend/igc-governance/pom.xml
COPY backend/igc-document/pom.xml backend/igc-document/pom.xml
COPY backend/igc-document-processing/pom.xml backend/igc-document-processing/pom.xml
COPY backend/igc-mcp-server/pom.xml backend/igc-mcp-server/pom.xml
COPY backend/igc-llm-orchestration/pom.xml backend/igc-llm-orchestration/pom.xml
COPY backend/igc-governance-enforcement/pom.xml backend/igc-governance-enforcement/pom.xml
COPY backend/igc-app-assembly/pom.xml backend/igc-app-assembly/pom.xml
COPY backend/igc-governance-hub/pom.xml backend/igc-governance-hub/pom.xml
COPY backend/igc-governance-hub-app/pom.xml backend/igc-governance-hub-app/pom.xml
COPY backend/igc-extraction-tika/pom.xml backend/igc-extraction-tika/pom.xml

WORKDIR /workspace/backend
RUN ./mvnw dependency:go-offline -DskipTests -B -pl igc-extraction-tika -am || true

# Layer 2: source + contracts. The contracts directory is essential for
# the igc-platform-audit jar's bundled schema and for the
# openapi-generator-maven-plugin run on this module.
WORKDIR /workspace
COPY backend backend
COPY contracts contracts

WORKDIR /workspace/backend
RUN ./mvnw -DskipTests package -pl igc-extraction-tika -am -o \
    || ./mvnw -DskipTests package -pl igc-extraction-tika -am

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/igc-extraction-tika/target/*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1
CMD ["java","-jar","/app/app.jar"]
