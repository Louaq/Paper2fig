# ---- build the Spring Boot jar from source ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
ENV MAVEN_OPTS="-Xmx1024m"
COPY backend/ /src/
RUN mvn -B -ntp -DskipTests -Dmaven.test.skip=true -pl yudao-server -am package

# ---- runtime: JRE for yudao + Python for the figure pipeline ----
FROM python:3.12-slim-bookworm
ENV DEBIAN_FRONTEND=noninteractive \
    TZ=Asia/Shanghai \
    PYTHONUNBUFFERED=1
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      openjdk-17-jre-headless libgomp1 ca-certificates curl tar \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/apps/yudao
COPY --from=build /src/yudao-server/target/yudao-server.jar \
     /opt/apps/yudao/yudao-server/target/yudao-server.jar
COPY pipeline/ /opt/apps/gallery-pipeline/
RUN python3 -m venv /opt/apps/gallery-pipeline/.venv \
 && /opt/apps/gallery-pipeline/.venv/bin/pip install --no-cache-dir \
      -r /opt/apps/gallery-pipeline/requirements.txt

COPY deploy/backend/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
