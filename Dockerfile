# syntax=docker/dockerfile:1
#
# The bird-platform live demo, as one self-contained image: the hand-built ripple SPA baked into a
# Spring Boot fat jar that serves it from the same origin, running the `demo` profile (Postgres only,
# no Elasticsearch). Build + run it with docker-compose.demo.yml.

# ── Stage 1: build the SPA (the ripple UI in ui/) ─────────────────────────────
FROM node:22-alpine AS ui
WORKDIR /ui
# Install deps against the lockfile first, so this layer caches unless the manifests change.
COPY ui/package.json ui/package-lock.json ./
RUN npm ci
COPY ui/ ./
# Same-origin in the container: the API is mounted under /api (demo profile, DemoWebConfiguration),
# so build the SPA to call /api instead of falling back to its in-memory mock.
ENV VITE_API_BASE=/api
RUN npm run build

# ── Stage 2: build the Spring Boot fat jar, SPA baked into its static resources ──
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
# Wrapper + build definition first for dependency-layer caching.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY api/ api/
COPY shared/ shared/
COPY contexts/ contexts/
# Spring Boot serves classpath:/static at the app root, so the built SPA ships inside the jar.
COPY --from=ui /ui/dist/ api/src/main/resources/static/
RUN ./gradlew :api:bootJar -x test --no-daemon

# ── Stage 3: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
# curl backs the container HEALTHCHECK; run as an unprivileged user.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 -s /usr/sbin/nologin appuser
# The bootJar is the only *-SNAPSHOT.jar (the plain library jar ends in -SNAPSHOT-plain.jar).
COPY --from=build /src/api/build/libs/api-*-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=demo
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
