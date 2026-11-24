# Paper2fig · 顶会论文主图灵感画廊

一个可自托管的 **AI 顶会论文 Figure 1 / Teaser 画廊 + 后台管理系统 + 采集/裁剪管线**。

前端是纯静态画廊，后台基于 [芋道 yudao](https://github.com/YunaiV/ruoyi-vue-pro)（Spring Boot 3 + Vue 3），
图片采集与裁剪由 Python + PyMuPDF + MinerU 完成。整套服务通过 Docker Compose 一键启动。

> **本仓库不包含图片数据。** 图片约占 450 MB，请参考 [`site/images/README.md`](site/images/README.md)
> 使用后台或管线自行采集，或把已有 `images/` 目录挂载/复制进来。

---

## 一键启动

```bash
# 1. 启动（首次会自动生成 .env 随机密码，并构建前后端镜像）
bash scripts/deploy.sh
```

也可以手动执行：

```bash
cp .env.example .env
# 编辑 .env，修改数据库、Redis、Meilisearch 密码
docker compose up -d --build
```

启动后：

| 地址 | 说明 |
|---|---|
| `http://localhost:8080/` | 静态画廊 |
| `http://localhost:8080/admin/` | 管理后台 |
| `admin / admin123` | 初始管理员账号，登录后请立即修改密码 |

首次启动需要：

1. MySQL 执行 `deploy/mysql/init/` 下的建表与种子 SQL；
2. Maven 构建后端 jar（首次构建会下载依赖，约数分钟）；
3. Python 安装管线依赖（PyMuPDF / onnxruntime 等）。

查看日志：

```bash
docker compose logs -f admin-api
docker compose logs -f web
```

停止 / 销毁：

```bash
docker compose down          # 保留数据卷
docker compose down -v       # 同时删除数据库、图片和配置卷（谨慎）
```

---

## 目录结构

```text
.
├── compose.yaml                  # 一键启动编排
├── .env.example                  # 环境变量模板
├── scripts/deploy.sh             # 生成 .env 并启动
├── deploy/
│   ├── web/                      # Nginx 镜像：静态站 + 后台 SPA + API 反代
│   ├── backend/                  # Maven 构建 + JRE/Python 运行时镜像
│   └── mysql/init/               # 建表、种子数据、环境变量注入
├── site/                         # 静态画廊（不含 images/ 图片）
│   ├── index.html
│   ├── assets/                   # JS/CSS/字体，figures.js 默认空数组
│   ├── data/                     # 站点配置与可选 figures.json
│   └── images/                   # 空目录结构，图片由用户提供
├── admin-ui/                     # 后台前端构建产物（直接由 Nginx 提供）
├── admin-ui-source/              # 后台前端 Vue 3 源码
├── backend/                      # yudao 后端源码（system / infra / gallery）
├── pipeline/                     # Python 采集、裁剪、质检、发布管线
└── docs/                         # 部署、数据与开发说明
```

运行时的共享数据卷：

| Volume | 容器路径 | 用途 |
|---|---|---|
| `site-data` | `/opt/1panel/www/sites/louaq.net/index` | 静态站；后台发布时直接写这里 |
| `files-data` | `/opt/1panel/www/sites/louaq.net/files` | 后台上传的头像、附件 |
| `pipeline-workspace` | `/opt/apps/gallery-pipeline/workspace` | PDF 缓存、裁剪中间产物 |
| `backups-data` | `/opt/backups` | 发布前自动备份 |
| `mysql-data` / `redis-data` / `meili-data` | 服务默认路径 | 数据库与索引 |

`site-data` 在首次启动时由 web 镜像写入初始静态站；后续后台发布写入同一卷，容器重建不会丢失。

---

## 组件

| 服务 | 镜像/构建 | 端口 | 说明 |
|---|---|---|---|
| `web` | `deploy/web/Dockerfile` | `${HTTP_PORT}:80` | Nginx：静态站、`/admin/`、`/admin-api/`、`/search`、SSE |
| `admin-api` | `deploy/backend/Dockerfile` | 内部 48080 | yudao 后台 API + JRE 17 + Python 管线 |
| `mysql` | `mysql:8.0` | 内部 3306 | 业务库 `yudao` |
| `redis` | `redis:7-alpine` | 内部 6379 | 缓存 / 会话 |
| `meilisearch` | `getmeili/meilisearch:v1.57.0` | 内部 7700 | 标题、作者全文检索 |

生产环境请在 `web` 前再放一层带 HTTPS 的反向代理（Caddy、Traefik、Nginx 均可），
把 `PUBLIC_BASE_URL` 和 `HTTP_PORT` 改成实际域名/端口，并只暴露反向代理端口。

---

## 图片与数据

- 图片目录约定：`images/<venue>/final/<id>.jpg` 与 `images/<venue>/thumb/<id>.webp`
- 卡片图由 `scripts` 或后台生成；前端用缩略图，灯箱用原图
- 完整说明见 [`docs/DATA.md`](docs/DATA.md)
- 后台首次进入时画廊为空；可以从现有 `figures.json` 导入，或通过「图片管理」新增，或运行采集管线

---

## 从源码构建 / 开发

```bash
# 后端
cd backend
mvn -DskipTests -pl yudao-server -am package

# 后端本地运行（需要 MySQL / Redis，自行修改 application-local.yaml）
java -jar yudao-server/target/yudao-server.jar

# 后台前端
cd admin-ui-source
pnpm install
pnpm build:prod
# 产物 dist-prod/ 对应发布用的 admin-ui/
```

Python 管线：

```bash
cd pipeline
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m pipeline.cli --help
```

更多说明见 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)。

---

## 安全清单

部署到公网前至少完成：

- [ ] 修改 `admin / admin123`
- [ ] 修改 `.env` 中全部数据库、Redis、Meilisearch 密码
- [ ] 配置 HTTPS，不要直接暴露 8080
- [ ] 设置 `PUBLIC_BASE_URL` 为真实 https 地址
- [ ] 定期备份 `mysql-data`、`site-data`、`files-data`、`backups-data`
- [ ] 限制 `/admin/` 访问来源（可选，在反向代理层做 IP 白名单或 Basic Auth）

---

## 开源与致谢

本项目基于以下开源项目与公开数据源构建：

- [topconf-paper-figure-gallery](https://github.com/qwdwqfwq/topconf-paper-figure-gallery) (MIT)
- [ruoyi-vue-pro / yudao](https://github.com/YunaiV/ruoyi-vue-pro)（MIT）
- [yudao-ui-admin-vue3](https://github.com/yudaocode/yudao-ui-admin-vue3)（MIT）
- [Meilisearch](https://github.com/meilisearch/meilisearch)（MIT）

本项目代码采用 MIT License，见 [`LICENSE`](LICENSE)。论文图片与论文内容的版权归原作者及出版方所有；
本仓库不包含图片数据，使用者需自行确认所采集内容的许可与使用范围。
