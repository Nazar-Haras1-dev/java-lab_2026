# Збірка (з кореня репозиторію): mvn -pl scheduler-service -am package
# Образ:                         docker build -t scheduler-service scheduler-service
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/scheduler-service.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
