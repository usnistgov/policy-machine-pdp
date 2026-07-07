FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY /target/admin-pdp-epp-1.0-SNAPSHOT.jar /app/admin-pdp-epp-1.0-SNAPSHOT.jar
EXPOSE 50052
ENTRYPOINT ["java", "-jar", "/app/admin-pdp-epp-1.0-SNAPSHOT.jar"]