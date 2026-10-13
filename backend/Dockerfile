FROM gradle:8.14.3-jdk21 AS build
WORKDIR /build
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system devinsight && useradd --system --gid devinsight devinsight && mkdir -p /app/data/uploads && chown -R devinsight:devinsight /app
COPY --from=build /build/build/libs/devinsight-0.1.0.jar /app/app.jar
USER devinsight
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
