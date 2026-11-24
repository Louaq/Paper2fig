# 生产部署建议

## 1. 反向代理 + HTTPS

Compose 默认只监听 `HTTP_PORT`（8080），不内置证书。生产环境建议在前面加 Caddy / Traefik / Nginx：

```nginx
server {
    listen 443 ssl http2;
    server_name example.com;
    ssl_certificate     /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;

    client_max_body_size 100m;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 300s;
    }
    location /infra/sse/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_read_timeout 3600s;
    }
}
```

`PUBLIC_BASE_URL` 必须改成 `https://example.com`，否则后台返回的文件 URL 会指向 localhost。

## 2. 资源和端口

- 只暴露反向代理的 443；不要暴露 MySQL / Redis / Meilisearch。
- 为 `site-data` 预留至少 5–10 GB（图片会持续增长）。
- 2 GB 内存机器建议 `JAVA_OPTS=-Xmx384m -XX:MaxMetaspaceSize=192m`；
  采集/裁剪是 CPU 与内存峰值任务，建议在低峰执行。

## 3. 升级

```bash
git pull
docker compose build
docker compose up -d
```

数据库结构升级按 `docs/specs` / 模块 `sql/` 中的增量脚本执行；不要直接删除 `mysql-data`。

## 4. 迁移

在旧机器执行：

```bash
docker compose down
docker run --rm -v paper2fig_site-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/site-data.tgz -C /data .
# mysql-data / files-data / backups-data 同理
```

新机器解压到对应 volume 后 `docker compose up -d` 即可。
