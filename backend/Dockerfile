ARG SERVICE=auth-service

FROM gradle:9-jdk21-alpine AS build
ARG SERVICE
WORKDIR /workspace
COPY settings.gradle build.gradle ./
COPY auth-service ./auth-service
COPY discovery-service ./discovery-service
RUN gradle :${SERVICE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/build/libs/*.jar app.jar
EXPOSE 8890 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
