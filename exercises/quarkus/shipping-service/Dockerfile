FROM registry.access.redhat.com/ubi10/openjdk-25:latest AS builder
WORKDIR /build

COPY --chown=185:0 pom.xml ./
COPY --chown=185:0 shared-observability/pom.xml ./shared-observability/
COPY --chown=185:0 order-service/pom.xml ./order-service/
COPY --chown=185:0 inventory-service/pom.xml ./inventory-service/
COPY --chown=185:0 payment-service/pom.xml ./payment-service/
COPY --chown=185:0 shipping-service/pom.xml ./shipping-service/
COPY --chown=185:0 notification-service/pom.xml ./notification-service/

RUN mvn -B -ntp -N install && \
    mvn -B -ntp -pl shared-observability,shipping-service -am dependency:go-offline || true

COPY --chown=185:0 shared-observability/src ./shared-observability/src
RUN mvn -B -ntp -pl shared-observability install -DskipTests

COPY --chown=185:0 shipping-service/src ./shipping-service/src
RUN mvn -B -ntp -pl shipping-service package -DskipTests

FROM registry.access.redhat.com/ubi10/openjdk-25-runtime:latest
ENV LANG=en_US.UTF-8 LANGUAGE=en_US:en \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
WORKDIR /deployments
COPY --from=builder --chown=185:0 /build/shipping-service/target/quarkus-app/lib/     ./lib/
COPY --from=builder --chown=185:0 /build/shipping-service/target/quarkus-app/*.jar    ./
COPY --from=builder --chown=185:0 /build/shipping-service/target/quarkus-app/app/     ./app/
COPY --from=builder --chown=185:0 /build/shipping-service/target/quarkus-app/quarkus/ ./quarkus/
EXPOSE 8083
USER 185
ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
