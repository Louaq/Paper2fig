#!/bin/sh
set -eu
mkdir -p \
  /opt/1panel/www/sites/louaq.net/index \
  /opt/1panel/www/sites/louaq.net/files \
  /opt/apps/gallery-pipeline/workspace \
  /opt/backups/fig-gallery
cd /opt/apps/yudao
exec java ${JAVA_OPTS:--Xmx512m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC} \
  -jar /opt/apps/yudao/yudao-server/target/yudao-server.jar
