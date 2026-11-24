-- 以 gallery_figure 作为「已发布目录」的唯一数据源 + 备份保留策略（2026-11-23）
SET NAMES utf8mb4;

ALTER TABLE `gallery_figure`
  ADD COLUMN `paper_id` bigint NULL COMMENT '关联 gallery_paper.id' AFTER `id`,
  ADD COLUMN `origin` varchar(16) NOT NULL DEFAULT 'IMPORT' COMMENT 'IMPORT/PIPELINE/MANUAL' AFTER `figure_id`,
  ADD COLUMN `manual_edited_time` datetime NULL COMMENT '最后一次人工编辑时间' AFTER `origin`,
  ADD KEY `idx_paper_id` (`paper_id`);

INSERT IGNORE INTO `gallery_setting` (`config_key`, `config_value`, `remark`) VALUES
 ('backup_dir', '/opt/backups/fig-gallery', '备份根目录（必须在网站根目录之外）'),
 ('backup_keep_days', '30', '备份保留天数'),
 ('backup_keep_min', '20', '至少保留最近 N 份备份'),
 ('backup_keep_max', '200', '备份份数硬上限'),
 ('backup_archive_days', '7', '超过 N 天转为 tar.gz 归档');
