# ===========================
# Stage 1: Build application
# ===========================
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app

RUN apt-get update && \
    apt-get install -y nodejs npm

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN gradle clean vaadinBuildFrontend bootJar --no-daemon

# ===========================
# Stage 2: Runtime image
# ===========================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/porssiohjain-*-SNAPSHOT.jar porssiohjain.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "porssiohjain.jar"]