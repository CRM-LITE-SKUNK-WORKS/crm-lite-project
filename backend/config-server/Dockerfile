FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml pom.xml
# crm-observability-starter (observability/resilience addendum) is not on Maven
# Central — install the parent POM (non-recursive) then the starter itself into
# this image's local repo, same pattern as the security-starter services.
RUN mvn -f pom.xml install -N -B
COPY backend/crm-observability-starter backend/crm-observability-starter
RUN mvn -f backend/crm-observability-starter/pom.xml install -DskipTests -B
COPY backend/config-server/pom.xml backend/config-server/pom.xml
RUN mvn -f backend/config-server/pom.xml dependency:go-offline -B
COPY backend/config-server/src backend/config-server/src
RUN mvn -f backend/config-server/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/config-server/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
