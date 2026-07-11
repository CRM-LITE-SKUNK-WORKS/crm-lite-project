FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml pom.xml
COPY backend/customer-service/pom.xml backend/customer-service/pom.xml
RUN mvn -f backend/customer-service/pom.xml dependency:go-offline -B
COPY backend/customer-service/src backend/customer-service/src
RUN mvn -f backend/customer-service/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/customer-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
