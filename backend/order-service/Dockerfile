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
COPY backend/order-service/pom.xml backend/order-service/pom.xml
RUN mvn -f backend/order-service/pom.xml dependency:go-offline -B
COPY backend/order-service/src backend/order-service/src
RUN mvn -f backend/order-service/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/order-service/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
