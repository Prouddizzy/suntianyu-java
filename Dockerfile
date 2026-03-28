# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

ENV SERVER_PORT=51002
ENV APP_DEVICE_TCP_PORT=51003

EXPOSE 51002 51003

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
