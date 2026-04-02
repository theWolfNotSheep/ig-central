FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Maven wrapper and POM files first (these change rarely)
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .
COPY bom/pom.xml bom/
COPY gls-platform/pom.xml gls-platform/
COPY gls-app-assembly/pom.xml gls-app-assembly/

# Download all dependencies (cached unless a POM changes)
RUN ./mvnw dependency:go-offline -DskipTests -B || true

# Now copy source code (this layer changes on every code change)
COPY . .

# Build — dependencies already cached, only compiles
RUN ./mvnw -DskipTests package -o || ./mvnw -DskipTests package

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/gls-app-assembly/target/*.jar /app/app.jar
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
