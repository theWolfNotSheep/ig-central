FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Maven wrapper and POM files first (these change rarely)
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .
COPY bom/pom.xml bom/
COPY gls-platform/pom.xml gls-platform/
COPY gls-governance/pom.xml gls-governance/
COPY gls-document/pom.xml gls-document/
COPY gls-document-processing/pom.xml gls-document-processing/
COPY gls-mcp-server/pom.xml gls-mcp-server/
COPY gls-llm-orchestration/pom.xml gls-llm-orchestration/
COPY gls-governance-enforcement/pom.xml gls-governance-enforcement/
COPY gls-app-assembly/pom.xml gls-app-assembly/

# Download dependencies for the modules we build
RUN ./mvnw dependency:go-offline -DskipTests -B -pl gls-app-assembly -am || true

# Now copy source code (this layer changes on every code change)
COPY . .

# Build only assembly and its dependencies
RUN ./mvnw -DskipTests package -pl gls-app-assembly -am -o || ./mvnw -DskipTests package -pl gls-app-assembly -am

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/gls-app-assembly/target/*.jar /app/app.jar
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
