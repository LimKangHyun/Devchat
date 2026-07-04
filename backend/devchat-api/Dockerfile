FROM eclipse-temurin:21-jre
WORKDIR /app
COPY devchat-api/build/libs/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=prod -jar app.jar"]