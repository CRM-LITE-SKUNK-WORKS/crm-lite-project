FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml pom.xml
COPY backend/api-gateway/pom.xml backend/api-gateway/pom.xml
RUN mvn -f backend/api-gateway/pom.xml dependency:go-offline -B
COPY backend/api-gateway/src backend/api-gateway/src
RUN mvn -f backend/api-gateway/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
