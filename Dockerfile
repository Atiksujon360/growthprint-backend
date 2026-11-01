
# Multi-stage Dockerfile for GrowthPrint Backend

# Stage 1: Build with Maven and Java 21
FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /app

COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw clean package -DskipTests -B \
    && test -n "$(find target -maxdepth 1 -name '*.jar' ! -name '*.original' -print -quit)"

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r growthprint \
    && useradd -r -g growthprint growthprint

COPY --from=build /app/target/*.jar /app/app.jar

USER growthprint

ENV PORT=10000

EXPOSE 10000

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -fsS "http://localhost:10000/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
