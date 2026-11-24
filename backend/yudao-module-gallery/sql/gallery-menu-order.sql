-- 画廊管理二级菜单顺序（2026-11-23）
-- 图片管理 / 论文库 / 论文日志 / 审核队列 / 发布管理 / 前端配置 / 管线设置 / 定时任务 / 调度日志
SET NAMES utf8mb4;

UPDATE `system_menu` SET `sort` = 10 WHERE `id` = 25007; -- 图片管理
UPDATE `system_menu` SET `sort` = 20 WHERE `id` = 25002; -- 论文库
UPDATE `system_menu` SET `sort` = 30 WHERE `id` = 25003; -- 裁剪日志
UPDATE `system_menu` SET `sort` = 40 WHERE `id` = 25004; -- 审核队列
UPDATE `system_menu` SET `sort` = 50 WHERE `id` = 25005; -- 发布管理
UPDATE `system_menu` SET `sort` = 60 WHERE `id` = 25008; -- 前端配置
UPDATE `system_menu` SET `sort` = 70 WHERE `id` = 25006; -- 管线设置
UPDATE `system_menu` SET `sort` = 80 WHERE `id` = 14;    -- 定时任务

-- 调度日志从「基础设施」移入「画廊管理」，排在最后
UPDATE `system_menu`
   SET `parent_id` = 25000, `path` = 'job-log', `sort` = 90
 WHERE `id` = 200;
