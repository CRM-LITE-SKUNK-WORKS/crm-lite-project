FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml pom.xml
COPY backend/lookup-service/pom.xml backend/lookup-service/pom.xml
RUN mvn -f backend/lookup-service/pom.xml dependency:go-offline -B
COPY backend/lookup-service/src backend/lookup-service/src
RUN mvn -f backend/lookup-service/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/lookup-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
