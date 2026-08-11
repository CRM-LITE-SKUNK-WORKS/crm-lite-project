FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml pom.xml
# The monorepo parent POM and crm-security-starter (ADR-009) are not on Maven Central.
# Install the parent POM (non-recursive), then build+install the starter, into this
# image's local repo so the service below can resolve them — dependency:go-offline
# reads the starter's descriptor, which in turn requires the parent POM to be present.
RUN mvn -f pom.xml install -N -B
COPY backend/crm-security-starter backend/crm-security-starter
RUN mvn -f backend/crm-security-starter/pom.xml install -DskipTests -B
# crm-observability-starter (observability/resilience addendum): same reasoning
# as crm-security-starter above — not on Maven Central, must be installed here.
COPY backend/crm-observability-starter backend/crm-observability-starter
RUN mvn -f backend/crm-observability-starter/pom.xml install -DskipTests -B
# crm-messaging-starter: same reasoning as the starters above — not on Maven
# Central, must be installed here.
COPY backend/crm-messaging-starter backend/crm-messaging-starter
RUN mvn -f backend/crm-messaging-starter/pom.xml install -DskipTests -B
COPY backend/account-service/pom.xml backend/account-service/pom.xml
RUN mvn -f backend/account-service/pom.xml dependency:go-offline -B
COPY backend/account-service/src backend/account-service/src
RUN mvn -f backend/account-service/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/account-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
