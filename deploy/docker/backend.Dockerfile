# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc AS build
WORKDIR /build
COPY pom.xml ./
COPY civicflow-common/ civicflow-common/
COPY civicflow-auth/ civicflow-auth/
COPY civicflow-resource/ civicflow-resource/
COPY civicflow-appointment/ civicflow-appointment/
COPY civicflow-queue/ civicflow-queue/
COPY civicflow-gateway/ civicflow-gateway/
# Windows checkout CRLF must not change the Linux formatter result.
RUN find . -name '*.java' -exec sed -i 's/\r$//' {} +
ARG MAVEN_PROXY_HOST=""
ARG MAVEN_PROXY_PORT=7897
RUN --mount=type=cache,id=civicflow-maven,target=/root/.m2,sharing=locked \
    set -eu; \
    if [ -n "$MAVEN_PROXY_HOST" ]; then \
        case "$MAVEN_PROXY_HOST" in *[!a-zA-Z0-9.:-]*) echo "Invalid Maven proxy host" >&2; exit 1;; esac; \
        case "$MAVEN_PROXY_PORT" in ''|*[!0-9]*) echo "Invalid Maven proxy port" >&2; exit 1;; esac; \
        [ "$MAVEN_PROXY_PORT" -ge 1 ] && [ "$MAVEN_PROXY_PORT" -le 65535 ]; \
        printf '<settings><proxies><proxy><id>build-proxy</id><active>true</active><protocol>http</protocol><host>%s</host><port>%s</port></proxy></proxies></settings>\n' "$MAVEN_PROXY_HOST" "$MAVEN_PROXY_PORT" > /tmp/maven-settings.xml; \
    else printf '<settings/>\n' > /tmp/maven-settings.xml; fi; \
    for attempt in 1 2 3; do \
        if mvn -s /tmp/maven-settings.xml -B -ntp -U -Daether.connector.http.retryHandler.count=5 -Daether.connector.connectTimeout=15000 -Daether.connector.requestTimeout=60000 -DskipTests package; then exit 0; fi; \
        if [ "$attempt" -eq 3 ]; then exit 1; fi; \
        echo "Maven build failed; retrying ($attempt/3) in 3 seconds..." >&2; \
        sleep 3; \
    done
COPY deploy/docker/Healthcheck.java /health/Healthcheck.java
RUN javac --release 17 /health/Healthcheck.java
ARG SERVICE
RUN cp civicflow-${SERVICE}/target/civicflow-${SERVICE}-*.jar /server.jar

FROM eclipse-temurin:17-jre-jammy@sha256:97137382c6f0c30427d9b7c44ad8b2d55ac823b0768c171d81a643ed219023c5
RUN groupadd --gid 10001 civicflow && useradd --uid 10001 --gid 10001 --create-home civicflow
WORKDIR /app
COPY --from=build /server.jar /app/server.jar
COPY --from=build /health/Healthcheck.class /app/health/Healthcheck.class
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx384m -Duser.timezone=Asia/Shanghai"
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/server.jar", "--spring.config.additional-location=file:/config/application.properties"]
