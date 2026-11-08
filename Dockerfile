# One Dockerfile for all four services. Select the service with --build-arg SERVICE=order-service
# Stage 1: build the jar (the shared modules are built too via -am)
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY pom.xml .
COPY events-contract/pom.xml events-contract/
COPY messaging-support/pom.xml messaging-support/
COPY testing-support/pom.xml testing-support/
COPY order-service/pom.xml order-service/
COPY inventory-service/pom.xml inventory-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
COPY events-contract/src events-contract/src
COPY messaging-support/src messaging-support/src
COPY testing-support/src testing-support/src
COPY ${SERVICE}/src ${SERVICE}/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl ${SERVICE} -am package -DskipTests

# Stage 2: small runtime image
FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar app.jar
RUN useradd --system app && chown app /app
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
