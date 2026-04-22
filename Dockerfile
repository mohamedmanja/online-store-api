FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/online-store-api-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=uat", "app.jar"]
