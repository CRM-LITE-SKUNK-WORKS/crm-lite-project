FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml pom.xml
COPY backend/discovery-server/pom.xml backend/discovery-server/pom.xml
RUN mvn -f backend/discovery-server/pom.xml dependency:go-offline -B
COPY backend/discovery-server/src backend/discovery-server/src
RUN mvn -f backend/discovery-server/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/discovery-server/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
