# ===========================
# Stage 1: Build application
# ===========================
FROM gradle:jdk21 AS build
WORKDIR /app

RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    node -v && npm -v

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