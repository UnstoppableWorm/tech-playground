FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN ["mvn", "--batch-mode", "--no-transfer-progress", "dependency:go-offline"]

COPY src ./src
RUN ["mvn", "--batch-mode", "--no-transfer-progress", "-DskipTests", "package"]

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN ["addgroup", "-S", "delivery"]
RUN ["adduser", "-S", "-G", "delivery", "delivery"]

COPY --from=build --chown=delivery:delivery /workspace/target/delivery-service.jar /app/delivery-service.jar

USER delivery

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/delivery-service.jar"]
