# Multi-stage build for gls-extraction-ocr.
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
COPY backend/gls-extraction-ocr/pom.xml backend/gls-extraction-ocr/pom.xml

WORKDIR /workspace/backend
RUN ./mvnw dependency:go-offline -DskipTests -B -pl gls-extraction-ocr -am || true

WORKDIR /workspace
COPY backend backend
COPY contracts contracts

WORKDIR /workspace/backend
RUN ./mvnw -DskipTests package -pl gls-extraction-ocr -am -o \
    || ./mvnw -DskipTests package -pl gls-extraction-ocr -am

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
COPY --from=build /workspace/backend/gls-extraction-ocr/target/*.jar /app/app.jar
EXPOSE 8091
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -sf http://localhost:8091/actuator/health || exit 1
CMD ["java","-jar","/app/app.jar"]
