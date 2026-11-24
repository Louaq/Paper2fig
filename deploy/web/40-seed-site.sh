#!/bin/sh
# Seed the shared site volume on first boot. Later admin publishes must survive
# container recreation, so only copy when index.html is absent.
set -eu
SITE=/opt/1panel/www/sites/louaq.net/index
FILES=/opt/1panel/www/sites/louaq.net/files
mkdir -p "$SITE" "$FILES"
if [ ! -f "$SITE/index.html" ]; then
  cp -a /opt/seed/site/. "$SITE/"
fi
