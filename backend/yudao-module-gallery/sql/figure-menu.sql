-- 前端图片管理 / 前端配置菜单（可重复执行）
SET NAMES utf8mb4;

INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`) VALUES
 (25007,'图片管理','',2,3,25000,'figure','ep:picture-rounded','gallery/figure/index','GalleryFigure',0,b'1',b'1',b'1','admin'),
 (25008,'前端配置','',2,7,25000,'site-config','ep:set-up','gallery/siteConfig/index','GallerySiteConfig',0,b'1',b'1',b'1','admin'),
 (25114,'图片查询','gallery:figure:query',3,1,25007,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25115,'图片更新','gallery:figure:update',3,2,25007,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25116,'图片删除','gallery:figure:delete',3,3,25007,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25117,'配置查询','gallery:site-config:query',3,1,25008,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25118,'配置更新','gallery:site-config:update',3,2,25008,'','',NULL,NULL,0,b'1',b'1',b'1','admin');

-- 调整已有菜单排序，让「图片管理」排在论文库之后
UPDATE `system_menu` SET `sort` = 4 WHERE `id` = 25003;
UPDATE `system_menu` SET `sort` = 5 WHERE `id` = 25004;
UPDATE `system_menu` SET `sort` = 6 WHERE `id` = 25005;
UPDATE `system_menu` SET `sort` = 8 WHERE `id` = 25006;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`tenant_id`)
SELECT 1, id, 'admin', 1 FROM `system_menu` WHERE id IN (25007, 25008, 25114, 25115, 25116, 25117, 25118);

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`tenant_id`)
SELECT 2, id, 'admin', 1 FROM `system_menu` WHERE id IN (25114, 25117);
