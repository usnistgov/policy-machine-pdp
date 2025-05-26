FROM eclipse-temurin:21-jdk-alpine

# create volume plugins
CMD mkdir /plugins
VOLUME /plugins

WORKDIR /app
COPY /target/resource-pdp-1.0-SNAPSHOT.jar /app/resource-pdp-1.0-SNAPSHOT.jar
EXPOSE ${GRPC_SERVER_PORT}
ENTRYPOINT ["java", "-jar", "/app/resource-pdp-1.0-SNAPSHOT.jar"]