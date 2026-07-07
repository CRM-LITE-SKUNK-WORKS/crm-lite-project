FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml pom.xml
COPY backend/auth-service/pom.xml backend/auth-service/pom.xml
RUN mvn -f backend/auth-service/pom.xml dependency:go-offline -B
COPY backend/auth-service/src backend/auth-service/src
RUN mvn -f backend/auth-service/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/auth-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
