FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean package

FROM eclipse-temurin:21.0.5_11-jre-alpine
RUN addgroup -S leadflow -g 10001 \
    && adduser -S leadflow -u 10001 -G leadflow
WORKDIR /app
COPY --from=build --chown=leadflow:leadflow /workspace/target/leadflow-api-0.1.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -q -O - http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"'
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
