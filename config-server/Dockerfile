FROM gradle:8.14.3-jdk21 AS build

WORKDIR /workspace
COPY . .
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /tmp/config
ENV XDG_CONFIG_HOME=/tmp/config
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
