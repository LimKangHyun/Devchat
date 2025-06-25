# ===== 1단계: 빌드 스테이지 =====
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# 의존성 캐싱을 위해 필요한 파일들 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# (선택) 의존성만 먼저 다운받아서 캐시 최적화
RUN ./gradlew dependencies --no-daemon || true

# 나머지 전체 복사
COPY src src

# 빌드 실행
RUN ./gradlew build -x test --no-daemon

# ===== 2단계: 런타임 스테이지 =====
FROM eclipse-temurin:21-jdk

WORKDIR /app

# 빌드 스테이지에서 생성된 JAR 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 실행
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]