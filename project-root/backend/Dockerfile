FROM openjdk:21
VOLUME /tmp
COPY SugarInventory-1.0-SNAPSHOT.jar app.jar
ENV JAR_FILE=app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
