# Extracting the Governance Hub into a Separate Project

## Instructions for Claude (or any developer)

The Governance Hub is built as two Maven modules within the IG Central monorepo but is designed for zero-dependency extraction into its own project. Follow these steps:

### 1. Create a new repository

```bash
mkdir governance-hub && cd governance-hub
git init
```

### 2. Copy the Hub modules

```bash
# From the IG Central repo:
cp -r backend/gls-governance-hub/ governance-hub/gls-governance-hub/
cp -r backend/gls-governance-hub-app/ governance-hub/gls-governance-hub-app/
cp backend/Dockerfile.hub governance-hub/Dockerfile
cp backend/.mvn governance-hub/.mvn -r
cp backend/mvnw governance-hub/
cp backend/mvnw.cmd governance-hub/
```

### 3. Create a new parent POM

The hub modules currently inherit from `gls-parent`. Create a new standalone parent:

```xml
<!-- governance-hub/pom.xml -->
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.2</version>
    </parent>
    <groupId>co.uk.wolfnotsheep.hub</groupId>
    <artifactId>governance-hub-parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <modules>
        <module>gls-governance-hub</module>
        <module>gls-governance-hub-app</module>
    </modules>
    <properties>
        <java.version>21</java.version>
    </properties>
</project>
```

### 4. Update child POMs

Change the parent reference in both `gls-governance-hub/pom.xml` and `gls-governance-hub-app/pom.xml`:

```xml
<parent>
    <groupId>co.uk.wolfnotsheep.hub</groupId>
    <artifactId>governance-hub-parent</artifactId>
    <version>1.0.0</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

### 5. Create standalone Docker Compose

```yaml
# governance-hub/docker-compose.yml
services:
  mongo:
    image: mongo:7
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD:-hubpassword}
    volumes:
      - mongo_data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  governance-hub:
    build: .
    environment:
      SPRING_DATA_MONGODB_URI: mongodb://root:${MONGO_PASSWORD:-hubpassword}@mongo:27017/governance_hub?authSource=admin
      HUB_ADMIN_USERNAME: ${HUB_ADMIN_USERNAME:-admin}
      HUB_ADMIN_PASSWORD: ${HUB_ADMIN_PASSWORD:-ChangeMe123!}
    ports:
      - "8090:8090"
    depends_on:
      mongo:
        condition: service_healthy

volumes:
  mongo_data:
```

### 6. What does NOT need to change

- **All Java code** — zero imports from `gls-platform`, `gls-governance`, or `gls-document`
- **All models/repositories** — self-contained in `gls-governance-hub`
- **Authentication** — uses its own API key system, not JWT/OAuth
- **Database** — uses its own MongoDB database (`governance_hub`), not the tenant database
- **Docker** — uses its own Dockerfile (`Dockerfile.hub`)

### 7. What tenant instances need

After extraction, IG Central instances connect to the hub via:
- `GOVERNANCE_HUB_URL` env var pointing to the hub's public URL (e.g. `https://hub.igcentral.com`)
- `GOVERNANCE_HUB_API_KEY` generated from the hub's admin API

### 8. Future: Add a web frontend

The hub could have its own admin UI (separate Next.js app) for:
- Managing governance packs (CRUD, version publishing)
- Managing API keys
- Viewing download analytics
- Reviewing community contributions

For now, admin operations are via the REST API (curl/Postman).
