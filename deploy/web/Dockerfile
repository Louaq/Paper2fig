FROM nginx:1.27-alpine
COPY deploy/web/default.conf.template /etc/nginx/templates/default.conf.template
COPY deploy/web/40-seed-site.sh /docker-entrypoint.d/40-seed-site.sh
RUN chmod +x /docker-entrypoint.d/40-seed-site.sh
COPY site /opt/seed/site
COPY admin-ui /usr/share/nginx/admin-ui
