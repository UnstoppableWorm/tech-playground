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

HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=18 \
  CMD ["wget", "-q", "-T", "3", "--spider", "http://127.0.0.1:8080/actuator/health"]

ENTRYPOINT ["java", "-jar", "/app/delivery-service.jar"]
