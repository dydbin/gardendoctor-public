FROM gradle:8.14.3-jdk17 AS build

WORKDIR /workspace/services/backend

COPY --chown=gradle:gradle services/backend/build.gradle services/backend/settings.gradle ./
COPY --chown=gradle:gradle services/backend/src ./src
COPY --chown=gradle:gradle infra/config/backend /workspace/infra/config/backend

RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /workspace/services/backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
