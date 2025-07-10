 # Java 17 기반의 경량화된 OpenJDK 이미지를 사용합니다.
FROM openjdk:17-jdk-slim

# 컨테이너 내부의 작업 디렉토리를 /app으로 설정합니다.

WORKDIR /app

# Gradle Wrapper를 복사합니다.
# build.gradle, settings.gradle, gradlew, gradlew.bat, gradle/wrapper 디렉토리가 필요합니다.
COPY build.gradle settings.gradle ./
COPY gradlew gradlew.bat ./
COPY gradle ./gradle



RUN chmod +x gradlew

RUN ./gradlew bootJar

# 빌드된 Spring Boot JAR 파일을 컨테이너로 복사합니다.
# 'build/libs/backend-0.0.1-SNAPSHOT.jar'는 Gradle 빌드 시 생성되는 기본 JAR 파일 이름입니다.
# 만약 JAR 파일 이름이 다르다면, 'backend-0.0.1-SNAPSHOT.jar' 부분을 실제 파일 이름으로 변경해야 합니다.
# (예: 'backend.jar' 또는 'your-project-name-0.0.1-SNAPSHOT.jar')
COPY build/libs/backend-0.0.1-SNAPSHOT.jar app.jar

# Spring Boot 애플리케이션이 기본적으로 사용하는 8080 포트를 외부에 노출합니다.
EXPOSE 8080

# 애플리케이션을 실행하는 명령어를 정의합니다.
ENTRYPOINT ["java", "-jar", "app.jar"]
