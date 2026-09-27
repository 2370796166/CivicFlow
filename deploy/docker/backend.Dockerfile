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
RUN --mount=type=cache,id=civicflow-maven,target=/root/.m2,sharing=locked mvn -B -ntp -DskipTests package
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
