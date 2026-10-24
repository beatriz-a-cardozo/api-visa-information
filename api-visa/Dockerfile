# syntax=docker/dockerfile:1
# Base images are pinned by digest; Dependabot opens a PR when a new one is published.

FROM eclipse-temurin:21-jdk-noble@sha256:107215954359f1ab0be058a247d7c64ed6e69c71c1682d471d38ef751ec8e964 AS build
WORKDIR /app

COPY --chmod=755 mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src ./src
# Tests, formatting and coverage already run in the CI before the image is built.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
    && java -Djarmode=tools -jar target/api-visa-*.jar extract --layers --launcher --destination extracted

# Distroless: no shell or package manager, runs as an unprivileged user.
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:1de29c4b0865b76fb878ba0a7b5a1d80db189db99cf46a5d88ec9c253b5cea9b
WORKDIR /app

COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
