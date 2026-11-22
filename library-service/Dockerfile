# Збірка (з кореня репозиторію): mvn -pl library-service -am package
# Образ:                         docker build -t library-service library-service
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/library-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
