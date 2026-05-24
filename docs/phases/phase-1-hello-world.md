# Phase 1 — Hello World

**Goal:** Prove that Docker Compose runs, the backend serves a JSON endpoint, and the frontend renders an Ant Design page. **No database. No auth. No business logic.**

**This phase exists to catch tooling and plumbing issues early, when there's nothing else to blame.**

---

## Scope

In:
- Docker Compose with two services: backend, frontend
- Backend: Spring Boot 3.5 + Java 25, one REST endpoint `GET /api/health` returning `{"status":"ok","service":"backend","jvm":"25.x.x"}`
- Frontend: React 18 + Vite + TypeScript + Ant Design 5, one page that says "Hello MarketingHub" inside an AntD Layout, and calls `/api/health` to display the JVM version

Out:
- Postgres (Phase 2)
- Redux (Phase 3)
- Auth, tenants, users, anything domain (later phases)
- Production Dockerfiles, optimizations

---

## Deliverables

### Repo root

- `.gitignore` covering Java/Node/IDE/env (`target/`, `dist/`, `node_modules/`, `.env`, `.idea/`, `.vscode/`)
- `.env.example` with placeholder values
- `docker-compose.yml` with two services:
  - `backend`: build `./backend`, expose 8080, healthcheck on `/api/health`
  - `frontend`: build `./frontend`, expose 5173 (nginx serving Vite build), depends_on backend
  - Network: `marketinghub-net`
- `README.md` with "how to run" instructions

### `backend/`

`pom.xml`:
- parent: `spring-boot-starter-parent` version `3.5.0` (or latest 3.5.x)
- `<java.version>25</java.version>`
- dependencies (minimal):
  - `spring-boot-starter-web`
  - `spring-boot-starter-actuator`
  - `lombok`
- build plugin: `spring-boot-maven-plugin`
- DO NOT add: data-jpa, security, flyway, postgresql, validation, anything else. Those come in later phases.

`src/main/java/com/marketinghub/MarketingHubApplication.java`:
- Standard Spring Boot main class with `@SpringBootApplication`
- Logs `Runtime.version()` at startup so we can see Java 25 confirmed

`src/main/java/com/marketinghub/health/HealthController.java`:
- `@RestController`
- `GET /api/health` returning a Java record `HealthResponse(String status, String service, String jvm)` with values `"ok"`, `"backend"`, `System.getProperty("java.version")`

`src/main/resources/application.yml`:
```yaml
server:
  port: 8080
spring:
  application:
    name: marketinghub-backend
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

`Dockerfile` (multi-stage):
```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 1001 app
USER app
COPY --from=build /build/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### `frontend/`

`package.json` dependencies:
- `react@^18.3.0`
- `react-dom@^18.3.0`
- `antd@^5.21.0`
- `@ant-design/icons@^5.5.0`

devDependencies:
- `typescript@^5.6.0`
- `vite@^5.4.0`
- `@vitejs/plugin-react@^4.3.0`
- `@types/react@^18.3.0`
- `@types/react-dom@^18.3.0`

`vite.config.ts`:
- Standard React plugin setup
- Dev server proxy: `/api` → `http://backend:8080` when in Docker (use `VITE_API_PROXY_TARGET` env, default `http://localhost:8080`)
- Build output to `dist/`

`tsconfig.json`:
- Strict mode on
- Path alias `@/*` → `src/*`

`src/main.tsx`:
- Renders `<App />` inside React `StrictMode` and AntD `ConfigProvider`

`src/App.tsx`:
- AntD `Layout` with `Header` (showing "MarketingHub") and `Content` (centered)
- Inside content: a `Card` with title "Hello MarketingHub" and body showing the backend health response
- On mount, fetch `/api/health`, display the `jvm` field (e.g., "Backend running on JVM 25.0.x")
- Loading and error states using AntD `Spin` and `Alert`

`src/index.css`:
- Reset basics, html/body 100% height

`Dockerfile` (multi-stage):
```dockerfile
FROM node:24-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

`nginx.conf`:
- SPA fallback: `try_files $uri /index.html;`
- Proxy `/api/` → `http://backend:8080/api/`
- gzip on

---

## Hard rules

- **No Postgres in compose yet.** Two services only.
- **No Redux yet.** Just React + AntD.
- **No Spring Security yet.** Spring Boot's default security autoconfiguration is removed because we didn't add `spring-boot-starter-security`. The `/api/health` endpoint is just open.
- **No Flyway, no JPA.** No DB-related dependency at all.

---

## Verification gate

In order, from the repo root. ALL must succeed:

```bash
# 1. Backend builds locally
cd backend && ./mvnw -q -DskipTests package
cd ..

# 2. Frontend builds locally
cd frontend && npm install && npm run build
cd ..

# 3. Compose config is valid
docker compose config > /dev/null

# 4. Stack comes up
docker compose up -d --build
sleep 30
docker compose ps           # both services running

# 5. Backend responds
curl -sf http://localhost:8080/api/health
# expected: {"status":"ok","service":"backend","jvm":"25.x.x"}

# 6. Frontend loads
curl -sfI http://localhost:5173 | head -n 1
# expected: HTTP/1.1 200 OK

# 7. Frontend → backend proxy works
curl -sf http://localhost:5173/api/health
# expected: same JSON as #5
```

Manual final check:
- Open http://localhost:5173 in a browser
- See "Hello MarketingHub" card with a line like "Backend running on JVM 25.0.x"

---

## Common failure modes (and fixes)

- **Backend container restarts:** check `docker compose logs backend`. Usually JVM mismatch or missing dependency.
- **Frontend can't reach backend:** check the nginx proxy config and that backend container is healthy.
- **`./mvnw` fails on Windows:** use `mvnw.cmd` instead, or just `mvn` since you have 3.9.16 installed locally.
- **Port 5173 or 8080 already in use:** stop whatever's using them, or change the compose port mapping.
- **TypeScript errors:** make sure `@types/react` major matches `react` major (both 18.x).

---

## Output expected at end of phase

1. File tree
2. Output of every verification command
3. Screenshot or curl proof that the browser flow works
4. "Phase 1 done — ready for Phase 2?"
