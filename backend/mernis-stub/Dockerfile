FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml pom.xml
COPY backend/mernis-stub/pom.xml backend/mernis-stub/pom.xml
RUN mvn -f backend/mernis-stub/pom.xml dependency:go-offline -B
COPY backend/mernis-stub/src backend/mernis-stub/src
RUN mvn -f backend/mernis-stub/pom.xml package -DskipTests -B

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/backend/mernis-stub/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
