FROM eclipse-temurin:21-jdk

WORKDIR /app

ARG JAR_FILE=build/libs/backend-0.0.1-SNAPSHOT.jar

# jar 파일 복제
COPY ${JAR_FILE} app.jar

# 인자 설정 부분과 jar 파일 복제 부분 합쳐서 진행해도 무방
#COPY build/libs/*.jar app.jar

# 실행 명령어
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]