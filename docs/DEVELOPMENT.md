# 开发与构建

## 技术栈

| 层 | 技术 |
|---|---|
| 静态站 | 原生 HTML/CSS/JS，无构建步骤 |
| 后台前端 | Vue 3 + Vite + Element Plus（`admin-ui-source/`） |
| 后台 API | Spring Boot 3.5 / Java 17 / MyBatis-Plus / Quartz |
| 采集管线 | Python 3，PyMuPDF、onnxruntime、MinerU API |
| 数据 | MySQL 8、Redis 7、Meilisearch |

## 后端

只需要以下 Maven 模块：

```text
backend/
├── pom.xml
├── yudao-dependencies/
├── yudao-framework/
├── yudao-server/
├── yudao-module-system/
├── yudao-module-infra/
└── yudao-module-gallery/
```

构建：

```bash
cd backend
mvn -DskipTests -pl yudao-server -am package
```

产物：`yudao-server/target/yudao-server.jar`。

Docker 镜像会额外安装 Python 3.12，并在
`/opt/apps/gallery-pipeline/.venv` 创建虚拟环境，因为 Java 后台通过子进程调用
`pipeline.cli`。默认路径写死在 `GallerySettingServiceImpl`，Docker 内保持相同路径。

## 后台前端

```bash
cd admin-ui-source
pnpm install
pnpm build:prod
```

产物 `dist-prod/` 对应仓库根目录的 `admin-ui/`。如果要修改后重新部署：

```bash
rm -rf admin-ui/*
cp -a admin-ui-source/dist-prod/. admin-ui/
docker compose build web && docker compose up -d web
```

## 数据库初始化

`deploy/mysql/init/` 按文件名顺序执行：

1. `01-schema.sql`：完整表结构（从运行中的 yudao 库导出，不含业务图片）；
2. `02-seed.sql`：菜单、角色、字典、定时任务、筛选项等初始化数据；
3. `03-bootstrap.sh`：注入 Meilisearch key、文件存储地址、管线默认参数。

初始管理员：`admin / admin123`（BCrypt，首次登录后请修改）。
如果需要修改初始密码，把 `02-seed.sql` 里的 BCrypt 换成新的哈希即可。

## 管线

`pipeline/` 来自生产环境的采集/裁剪代码，`requirements.txt` 为冻结依赖。
运行时 Java 调用：

```text
/opt/apps/gallery-pipeline/.venv/bin/python -m pipeline.cli ...
```

工作目录是 `/opt/apps/gallery-pipeline`，`workspace/` 使用 Docker volume 持久化。

## 本地调试

```bash
# 只启动依赖
docker compose up -d mysql redis meilisearch

# 本地跑后端（数据库/Redis 需映射到宿主机，或改 application-local.yaml）
cd backend
SPRING_PROFILES_ACTIVE=local,docker \
DB_URL='jdbc:mysql://127.0.0.1:3306/yudao?...' \
mvn -pl yudao-server -am spring-boot:run
```

## 常见问题

**首次构建很慢**：Maven 要下载 Spring Boot 依赖，属于正常现象；后续层会缓存。

**后台上传的图片 404**：检查 `PUBLIC_BASE_URL` 是否与访问地址一致，以及 `files-data` 是否挂载到 web 与 admin-api 两个服务。

**搜索无结果**：确认 `/search` 请求返回 200；Meilisearch 索引在后台首次发布或启动同步后生成。可查看 `docker compose logs admin-api | grep meili`。
