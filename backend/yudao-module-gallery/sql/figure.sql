-- ---------------------------------------------------------------
-- 前端静态站（louaq.net）图片与筛选项管理
-- ---------------------------------------------------------------
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `gallery_figure` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `figure_id` varchar(255) NOT NULL COMMENT '静态站图片 ID（figures.json 的 id）',
  `venue` varchar(16) NOT NULL DEFAULT '' COMMENT '会议',
  `year` int NOT NULL DEFAULT 0 COMMENT '年份',
  `title` varchar(512) NOT NULL DEFAULT '' COMMENT '标题',
  `authors` text NULL COMMENT '作者 JSON 数组',
  `pattern` varchar(32) NULL DEFAULT NULL COMMENT '视觉模式',
  `tier` varchar(16) NULL DEFAULT NULL COMMENT '等级 oral/spotlight',
  `award` varchar(16) NULL DEFAULT NULL COMMENT '奖项 best/honorable',
  `image` varchar(512) NOT NULL DEFAULT '' COMMENT '图片相对路径',
  `paper` varchar(512) NULL DEFAULT NULL COMMENT '论文链接',
  `pdf_source` varchar(512) NULL DEFAULT NULL COMMENT 'PDF 链接',
  `score` double NULL DEFAULT NULL COMMENT '评分',
  `width` int NULL DEFAULT NULL COMMENT '图片宽度',
  `height` int NULL DEFAULT NULL COMMENT '图片高度',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_figure_id` (`figure_id`),
  KEY `idx_venue_year` (`venue`, `year`),
  KEY `idx_tier` (`tier`),
  KEY `idx_pattern` (`pattern`),
  KEY `idx_award` (`award`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='静态画廊图片';

CREATE TABLE IF NOT EXISTS `gallery_filter_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `dimension` varchar(16) NOT NULL COMMENT '维度：venue/year/tier/pattern',
  `option_value` varchar(64) NOT NULL COMMENT '选项值',
  `label_zh` varchar(64) NULL DEFAULT NULL COMMENT '中文显示名',
  `label_en` varchar(64) NULL DEFAULT NULL COMMENT '英文显示名',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dim_value` (`dimension`, `option_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='静态画廊筛选项配置';

INSERT IGNORE INTO `gallery_filter_option` (`dimension`,`option_value`,`label_zh`,`label_en`,`sort`) VALUES
 ('venue','iclr','ICLR','ICLR',10),
 ('venue','icml','ICML','ICML',20),
 ('venue','neurips','NeurIPS','NeurIPS',30),
 ('venue','cvpr','CVPR','CVPR',40),
 ('venue','acl','ACL','ACL',50),
 ('venue','aaai','AAAI','AAAI',60),
 ('year','2026','2026','2026',10),
 ('year','2025','2025','2025',20),
 ('year','2024','2024','2024',30),
 ('year','2023','2023','2023',40),
 ('tier','best','最佳论文','Best Paper',10),
 ('tier','oral','口头报告','Oral',20),
 ('tier','spotlight','焦点论文','Spotlight',30),
 ('pattern','teaser','主视觉','Teaser',10),
 ('pattern','conceptual','概念图','Conceptual',20),
 ('pattern','framework','框架总览','Framework',30),
 ('pattern','pipeline','流程图','Pipeline',40),
 ('pattern','architecture','架构图','Architecture',50),
 ('pattern','taxonomy','全景 / 基准','Taxonomy / Benchmark',60),
 ('pattern','results','结果','Results',70),
 ('pattern','comparison','对比','Comparison',80);
