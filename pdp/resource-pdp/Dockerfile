FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app
COPY /target/resource-pdp-1.0-SNAPSHOT.jar /app/resource-pdp-1.0-SNAPSHOT.jar
EXPOSE ${RESOURCE_GRPC_SERVER_PORT}
ENTRYPOINT ["java", "-jar", "/app/resource-pdp-1.0-SNAPSHOT.jar"]