# Multi-stage build for igc-enforcement-worker.
# Repo-root context — see docs/service-template.md.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

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
COPY backend/igc-extraction-archive/pom.xml backend/igc-extraction-archive/pom.xml
COPY backend/igc-extraction-ocr/pom.xml backend/igc-extraction-ocr/pom.xml
COPY backend/igc-extraction-audio/pom.xml backend/igc-extraction-audio/pom.xml
COPY backend/igc-classifier-router/pom.xml backend/igc-classifier-router/pom.xml
COPY backend/igc-bert-inference/pom.xml backend/igc-bert-inference/pom.xml
COPY backend/igc-slm-worker/pom.xml backend/igc-slm-worker/pom.xml
COPY backend/igc-llm-worker/pom.xml backend/igc-llm-worker/pom.xml

WORKDIR /workspace/backend
RUN ./mvnw dependency:go-offline -DskipTests -B -pl igc-governance-enforcement -am || true

WORKDIR /workspace
COPY backend backend
COPY contracts contracts

WORKDIR /workspace/backend
RUN ./mvnw -DskipTests package -pl igc-governance-enforcement -am -o \
    || ./mvnw -DskipTests package -pl igc-governance-enforcement -am

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/igc-governance-enforcement/target/*-exec.jar /app/app.jar
EXPOSE 8097
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -sf http://localhost:8097/actuator/health || exit 1
CMD ["java","-jar","/app/app.jar"]
