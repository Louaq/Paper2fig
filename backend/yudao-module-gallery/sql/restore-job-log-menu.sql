-- 恢复「基础设施 → 调度日志」二级菜单（2026-11-23）
SET NAMES utf8mb4;

INSERT IGNORE INTO `system_menu`
 (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`)
VALUES
 (200,'调度日志','',2,90,25000,'job-log','ep:document','infra/job/logger/index','InfraJobLog',0,b'1',b'1',b'1','admin');

-- 超级管理员可见
INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`tenant_id`)
VALUES (1,200,'admin',1);
