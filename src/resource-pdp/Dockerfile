FROM eclipse-temurin:21-jre-alpine

# create volume plugins
RUN mkdir -p /plugins
VOLUME /plugins

WORKDIR /app
COPY /target/resource-pdp-1.0-SNAPSHOT.jar /app/resource-pdp-1.0-SNAPSHOT.jar
EXPOSE ${GRPC_SERVER_PORT}
ENTRYPOINT ["java", "-jar", "/app/resource-pdp-1.0-SNAPSHOT.jar"]