FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app
COPY /target/admin-pdp-epp-1.0-SNAPSHOT.jar /app/admin-pdp-epp-1.0-SNAPSHOT.jar
EXPOSE ${ADMIN_GRPC_SERVER_PORT}
ENTRYPOINT ["java", "-jar", "/app/admin-pdp-epp-1.0-SNAPSHOT.jar"]