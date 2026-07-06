# syntax=docker/dockerfile:1
# Multi-stage build: resolves dependencies in a cached layer, produces a Spring
# Boot layered jar, then assembles a slim non-root runtime image.
ARG SERVICE=auth-service

# --- 1. Dependency cache + build -------------------------------------------
FROM gradle:9-jdk21-alpine AS build
ARG SERVICE
WORKDIR /workspace
# Copy only build scripts first so the dependency layer is reused unless the
# gradle files themselves change (source edits no longer re-download deps).
COPY settings.gradle build.gradle ./
COPY auth-service/build.gradle ./auth-service/
COPY news-service/build.gradle ./news-service/
COPY chat-service/build.gradle ./chat-service/
COPY shopping-service/build.gradle ./shopping-service/
RUN gradle :${SERVICE}:dependencies --no-daemon --no-configuration-cache || true
# Now bring in the sources and build the bootable jar.
COPY auth-service ./auth-service
COPY news-service ./news-service
COPY chat-service ./chat-service
COPY shopping-service ./shopping-service
RUN gradle :${SERVICE}:bootJar --no-daemon \
    && cp ${SERVICE}/build/libs/*.jar /workspace/app.jar

# --- 2. Explode the Spring Boot layered jar --------------------------------
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /layers
COPY --from=build /workspace/app.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# --- 3. Slim runtime image (non-root, container-aware JVM) -----------------
FROM eclipse-temurin:21-jre-alpine
ARG APP_PORT=8890
ARG HEALTH_PATH=/actuator/health
ENV APP_PORT=${APP_PORT} \
    HEALTH_PATH=${HEALTH_PATH} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
# Layers ordered least-to-most volatile for optimal image-layer caching.
COPY --from=layers /layers/dependencies/ ./
COPY --from=layers /layers/spring-boot-loader/ ./
COPY --from=layers /layers/snapshot-dependencies/ ./
COPY --from=layers /layers/application/ ./
USER spring:spring
EXPOSE 8890 8761 8891 8892 8893
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=10 \
  CMD wget -qO- "http://localhost:${APP_PORT}${HEALTH_PATH}" >/dev/null 2>&1 || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
