FROM eclipse-temurin:17-jdk AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon \
    && cp "$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print -quit)" /workspace/app.jar

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S sairo && adduser -S sairo -G sairo

WORKDIR /app
COPY --from=builder --chown=sairo:sairo /workspace/app.jar ./app.jar

USER sairo
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
