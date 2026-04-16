FROM eclipse-temurin:17-jre

WORKDIR /app

ARG JAR_FILE=target/*.jar

COPY target/service-b-1.0.0.jar app.jar


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
