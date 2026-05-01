# Multi-stage build for igc-extraction-ocr.
#
# Build context: repo root (NOT backend/) — see docs/service-template.md.
# Runtime image installs `tesseract-ocr` + the configured language
# packs via apt; Tess4J's JNA bridge loads libtesseract on first use.
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

WORKDIR /workspace/backend
RUN ./mvnw dependency:go-offline -DskipTests -B -pl igc-extraction-ocr -am || true

WORKDIR /workspace
COPY backend backend
COPY contracts contracts

WORKDIR /workspace/backend
RUN ./mvnw -DskipTests package -pl igc-extraction-ocr -am -o \
    || ./mvnw -DskipTests package -pl igc-extraction-ocr -am

FROM eclipse-temurin:25-jre

# OCR_LANGUAGES build-arg = space-separated list of Tesseract language
# packs to install. Default `eng`. Override via build args, e.g.:
#   docker build --build-arg OCR_LANGUAGES="eng fra deu" .
ARG OCR_LANGUAGES="eng"
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tesseract-ocr \
    && for L in ${OCR_LANGUAGES}; do \
         if [ "$L" != "eng" ]; then apt-get install -y --no-install-recommends "tesseract-ocr-$L"; fi; \
       done \
    && rm -rf /var/lib/apt/lists/*

# Debian's tesseract-ocr 5.x ships tessdata at /usr/share/tesseract-ocr/5/tessdata
# (not 4.00). Set TESSDATA_PREFIX so Tess4J + the health indicator
# both look at the right place out-of-the-box.
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

WORKDIR /app
COPY --from=build /workspace/backend/igc-extraction-ocr/target/*.jar /app/app.jar
EXPOSE 8091
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -sf http://localhost:8091/actuator/health || exit 1
CMD ["java","-jar","/app/app.jar"]
