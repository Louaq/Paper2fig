#!/bin/sh
# Runs once, after 01-schema.sql and 02-seed.sql, on an empty MySQL data volume.
# Injects deployment-specific values that cannot be baked into the SQL dump.
set -eu

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${MEILI_MASTER_KEY:?MEILI_MASTER_KEY is required}"
DB="${MYSQL_DATABASE:-yudao}"
BASE_URL="${PUBLIC_BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"

case "$MEILI_MASTER_KEY" in
  *"'"*) echo "MEILI_MASTER_KEY must not contain a single quote" >&2; exit 1 ;;
esac
case "$BASE_URL" in
  *"'"*) echo "PUBLIC_BASE_URL must not contain a single quote" >&2; exit 1 ;;
esac

mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "$DB" <<SQL
INSERT INTO \`gallery_setting\` (\`config_key\`, \`config_value\`, \`remark\`, \`creator\`) VALUES
 ('python', '/opt/apps/gallery-pipeline/.venv/bin/python', 'Python 解释器', 'admin'),
 ('workdir', '/opt/apps/gallery-pipeline', '管线工作目录', 'admin'),
 ('site_dir', '/opt/1panel/www/sites/louaq.net/index', '静态站根目录', 'admin'),
 ('backup_dir', '/opt/backups/fig-gallery', '备份目录', 'admin'),
 ('dpi', '200', '裁剪渲染 DPI', 'admin'),
 ('max_pages', '6', '扫描页数', 'admin'),
 ('include_caption', 'false', '是否把图注一起裁进图片', 'admin'),
 ('dup_hamming', '6', '感知哈希重复阈值', 'admin'),
 ('min_confidence_auto', '0.85', '高置信度阈值', 'admin'),
 ('publish_update_sitemap', 'true', '发布时同步 sitemap', 'admin'),
 ('backup_keep_days', '30', '备份保留天数', 'admin'),
 ('backup_keep_min', '20', '备份最少保留份数', 'admin'),
 ('backup_keep_max', '200', '备份最多保留份数', 'admin'),
 ('backup_archive_days', '7', '备份归档天数', 'admin'),
 ('mineru_api_base', 'https://mineru.net', 'MinerU 接口地址', 'admin'),
 ('mineru_api_token', '', 'MinerU Token（按需填写）', 'admin'),
 ('mineru_model', 'pipeline', 'MinerU 模型', 'admin'),
 ('mineru_batch_size', '50', 'MinerU 批大小', 'admin'),
 ('proxy', '', '采集代理（可选）', 'admin'),
 ('meili_url', 'http://meilisearch:7700', 'Meilisearch 地址', 'admin'),
 ('meili_index', 'figures', 'Meilisearch 索引名', 'admin'),
 ('meili_api_key', '${MEILI_MASTER_KEY}', 'Meilisearch Master Key', 'admin'),
 ('meili_enabled', 'true', '启用全文检索', 'admin'),
 ('meili_sync_on_startup', 'true', '启动时同步检索索引', 'admin')
ON DUPLICATE KEY UPDATE \`config_value\` = VALUES(\`config_value\`);

INSERT INTO \`infra_file_config\`
 (\`id\`, \`name\`, \`storage\`, \`remark\`, \`master\`, \`config\`, \`creator\`, \`updater\`)
VALUES
 (37, '本地存储', 10, 'Docker 本地文件存储', b'1',
  '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClientConfig","basePath":"/opt/1panel/www/sites/louaq.net/files","domain":"${BASE_URL}"}',
  'admin', 'admin')
ON DUPLICATE KEY UPDATE \`config\` = VALUES(\`config\`), \`updater\` = 'admin';
SQL
