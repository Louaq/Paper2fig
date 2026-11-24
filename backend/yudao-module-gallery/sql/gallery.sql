-- ---------------------------------------------------------------
-- 顶会主图画廊 业务表（芋道 yudao：cn.iocoder.yudao.module.gallery）
-- 依赖 yudao 的 system_menu / system_role_menu，管理员角色 id = 1
-- ---------------------------------------------------------------
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `gallery_paper`;
CREATE TABLE `gallery_paper` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `venue` varchar(16) NOT NULL DEFAULT '' COMMENT '会议',
  `year` int NOT NULL DEFAULT 0 COMMENT '年份',
  `source` varchar(32) NOT NULL DEFAULT '' COMMENT '来源适配器',
  `source_id` varchar(128) NOT NULL DEFAULT '' COMMENT '来源侧论文 ID',
  `dedupe_key` varchar(255) NULL DEFAULT NULL COMMENT '去重键',
  `title` varchar(512) NOT NULL DEFAULT '' COMMENT '标题',
  `title_norm` varchar(512) NOT NULL DEFAULT '' COMMENT '归一化标题',
  `authors` varchar(2048) NULL DEFAULT NULL COMMENT '作者 JSON 数组',
  `abstract_text` text NULL COMMENT '摘要',
  `paper_url` varchar(512) NULL DEFAULT NULL COMMENT '原文链接',
  `pdf_url` varchar(512) NULL DEFAULT NULL COMMENT 'PDF 链接',
  `doi` varchar(128) NULL DEFAULT NULL,
  `arxiv_id` varchar(64) NULL DEFAULT NULL,
  `decision` varchar(32) NULL DEFAULT NULL COMMENT 'oral/spotlight/poster',
  `track` varchar(128) NULL DEFAULT NULL,
  `pattern` varchar(32) NULL DEFAULT NULL COMMENT '视觉模式',
  `tier` varchar(16) NULL DEFAULT NULL,
  `award` varchar(16) NULL DEFAULT NULL,
  `score` double NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 待处理 1 已裁剪 2 通过 3 拒绝 4 已发布',
  `pdf_status` tinyint NOT NULL DEFAULT 0 COMMENT '0 未缓存 1 已缓存 2 失败',
  `pdf_path` varchar(512) NULL DEFAULT NULL,
  `pdf_pages` int NULL DEFAULT NULL,
  `pdf_size` bigint NULL DEFAULT NULL,
  `pdf_error` varchar(512) NULL DEFAULT NULL,
  `pdf_time` datetime NULL DEFAULT NULL,
  `crop_id` bigint NULL DEFAULT NULL,
  `published_time` datetime NULL DEFAULT NULL,
  `remark` varchar(500) NULL DEFAULT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source` (`source`, `source_id`),
  KEY `idx_venue_year` (`venue`, `year`),
  KEY `idx_status` (`status`),
  KEY `idx_title_norm` (`title_norm`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画廊论文库';

DROP TABLE IF EXISTS `gallery_job`;
CREATE TABLE `gallery_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kind` varchar(16) NOT NULL DEFAULT '' COMMENT 'CRAWL/FETCH/CROP/PUBLISH',
  `venue` varchar(16) NULL DEFAULT NULL,
  `year` int NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 等待 1 运行 2 成功 3 失败 4 取消',
  `progress` int NULL DEFAULT 0,
  `total` int NULL DEFAULT NULL,
  `done` int NULL DEFAULT NULL,
  `failed` int NULL DEFAULT NULL,
  `params_json` text NULL,
  `result_json` text NULL,
  `log_path` varchar(512) NULL DEFAULT NULL,
  `pid` bigint NULL DEFAULT NULL,
  `error_msg` varchar(1024) NULL DEFAULT NULL,
  `started_time` datetime NULL DEFAULT NULL,
  `finished_time` datetime NULL DEFAULT NULL,
  `dedupe_key` varchar(128) NULL DEFAULT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_kind_status` (`kind`, `status`),
  KEY `idx_dedupe` (`dedupe_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画廊管线任务';

DROP TABLE IF EXISTS `gallery_crop`;
CREATE TABLE `gallery_crop` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paper_id` bigint NOT NULL,
  `job_id` bigint NULL DEFAULT NULL,
  `page_no` int NULL DEFAULT NULL,
  `rect` varchar(128) NULL DEFAULT NULL COMMENT 'PDF 坐标',
  `dpi` int NULL DEFAULT NULL,
  `width` int NULL DEFAULT NULL,
  `height` int NULL DEFAULT NULL,
  `file_size` bigint NULL DEFAULT NULL,
  `image_path` varchar(512) NULL DEFAULT NULL,
  `confidence` double NULL DEFAULT NULL,
  `quality` double NULL DEFAULT NULL,
  `scores_json` varchar(1024) NULL DEFAULT NULL,
  `flags_json` varchar(1024) NULL DEFAULT NULL,
  `qc_json` text NULL,
  `phash` varchar(64) NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 待审 1 通过 2 拒绝',
  `reviewer` varchar(64) NULL DEFAULT NULL,
  `review_time` datetime NULL DEFAULT NULL,
  `review_comment` varchar(500) NULL DEFAULT NULL,
  `algorithm` varchar(64) NULL DEFAULT NULL,
  `diagnostics` mediumtext NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_paper` (`paper_id`),
  KEY `idx_status_conf` (`status`, `confidence`),
  KEY `idx_phash` (`phash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='裁剪结果与质检';

DROP TABLE IF EXISTS `gallery_publish`;
CREATE TABLE `gallery_publish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` varchar(32) NULL DEFAULT NULL,
  `site_dir` varchar(512) NULL DEFAULT NULL,
  `added` int NULL DEFAULT NULL,
  `updated` int NULL DEFAULT NULL,
  `total` int NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 预检 1 成功 2 失败',
  `backup_dir` varchar(512) NULL DEFAULT NULL,
  `result_json` text NULL,
  `job_id` bigint NULL DEFAULT NULL,
  `operator_name` varchar(64) NULL DEFAULT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画廊发布记录';

DROP TABLE IF EXISTS `gallery_setting`;
CREATE TABLE `gallery_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(64) NOT NULL,
  `config_value` varchar(1024) NULL DEFAULT NULL,
  `remark` varchar(255) NULL DEFAULT NULL,
  `creator` varchar(64) NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画廊管线参数';

INSERT INTO `gallery_setting` (`config_key`, `config_value`, `remark`) VALUES
 ('python', '/opt/apps/gallery-pipeline/.venv/bin/python', 'Python 解释器'),
 ('workdir', '/opt/apps/gallery-pipeline', '管线工作目录'),
 ('site_dir', '/opt/1panel/www/sites/louaq.net/index', '静态站根目录'),
 ('dpi', '200', '裁剪渲染 DPI'),
 ('max_pages', '6', '扫描页数'),
 ('include_caption', 'false', '是否把图注一起裁进图片'),
 ('dup_hamming', '6', '感知哈希重复阈值'),
 ('min_confidence_auto', '0.85', '高置信度阈值（仅提示，仍然全部人工审核）'),
 ('publish_update_sitemap', 'true', '发布时同步 sitemap');
