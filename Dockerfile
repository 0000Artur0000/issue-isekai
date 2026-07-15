FROM eclipse-temurin:25-jdk-alpine AS java25

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY --from=java25 /opt/java/openjdk /opt/java25/openjdk
COPY . .
RUN ./gradlew :panel:bootJar -Porg.gradle.java.installations.paths=/opt/java25/openjdk --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/panel/build/libs/panel-0.1.0-SNAPSHOT.jar /app/issue-isekai-panel.jar
COPY scripts/docker-entrypoint.sh /usr/local/bin/issue-isekai-entrypoint
RUN mkdir -p /data/resource-packs \
    && chown -R 10001:10001 /data/resource-packs \
    && chmod 755 /usr/local/bin/issue-isekai-entrypoint
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/issue-isekai-entrypoint"]
