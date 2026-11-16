# One Dockerfile for all four services. Select the service with --build-arg SERVICE=order-service
# Stage 1: build the jar. There is no parent/aggregator pom: each module is its own Maven project, so the
# shared libraries are installed into the (cached) local repository first, then the service is packaged.
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY events-contract events-contract
COPY messaging-support messaging-support
COPY testing-support testing-support
COPY ${SERVICE} ${SERVICE}
RUN --mount=type=cache,target=/root/.m2 \
    for m in events-contract messaging-support testing-support; do mvn -q -B -f $m install -DskipTests || exit 1; done \
    && mvn -q -B -f ${SERVICE} package -DskipTests

# Stage 2: small runtime image
FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar app.jar
RUN useradd --system app && chown app /app
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
