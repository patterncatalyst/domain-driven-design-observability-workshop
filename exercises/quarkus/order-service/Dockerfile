# Multi-stage Dockerfile for Quarkus services
# Stage 1: UBI9 + OpenJDK 25 + Maven builds the app
# Stage 2: UBI9 + OpenJDK 25 JRE runs the app

# ---- Stage 1: build ---------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-25:latest AS builder

WORKDIR /build

COPY --chown=185:0 pom.xml ./
COPY --chown=185:0 mvnw ./
COPY --chown=185:0 .mvn ./.mvn

RUN ./mvnw -B -ntp dependency:go-offline

COPY --chown=185:0 src ./src
RUN ./mvnw -B -ntp package -DskipTests

# ---- Stage 2: runtime -------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:latest

ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

WORKDIR /deployments

COPY --from=builder --chown=185:0 /build/target/quarkus-app/lib/        ./lib/
COPY --from=builder --chown=185:0 /build/target/quarkus-app/*.jar       ./
COPY --from=builder --chown=185:0 /build/target/quarkus-app/app/        ./app/
COPY --from=builder --chown=185:0 /build/target/quarkus-app/quarkus/    ./quarkus/

EXPOSE 8080

USER 185

ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
