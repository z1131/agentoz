FROM openjdk:17-jdk-slim

LABEL maintainer="Agent Platform"

WORKDIR /app

COPY target/agent-platform-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 20880 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
