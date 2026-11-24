-- 把「定时任务」从 基础设施 移到 画廊管理 下（2026-11-23）
-- path 改为 crawl-job，避免与画廊管理下已有的「论文日志」(path=paper-log) 冲突
UPDATE `system_menu`
   SET `parent_id` = 25000,
       `path` = 'crawl-job',
       `sort` = 9
 WHERE `id` = 14;
