# Stage 1: Build the application
FROM gradle:jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN gradle bootJar --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /app/build/libs/porssiohjain-*-SNAPSHOT.jar porssiohjain.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","porssiohjain.jar"]
