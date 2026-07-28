# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY docker/maven-settings.xml ./maven-settings.xml

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -s /workspace/maven-settings.xml -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S -g 10001 wbe \
    && adduser -S -D -H -u 10001 -G wbe wbe \
    && mkdir -p /opt/wbe /var/lib/wbe-backup/uploads /var/log/wbe-backup \
    && chown -R wbe:wbe /opt/wbe /var/lib/wbe-backup /var/log/wbe-backup

WORKDIR /opt/wbe
COPY --from=build --chown=wbe:wbe /workspace/target/web-backup-*.jar app.jar

USER 10001:10001

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=70.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=20 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/opt/wbe/app.jar"]
