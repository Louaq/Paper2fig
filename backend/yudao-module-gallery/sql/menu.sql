-- 画廊管理菜单（可重复执行）
SET NAMES utf8mb4;
INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`) VALUES
 (25000,'画廊管理','',1,5,0,'/gallery','ep:picture',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25002,'论文库','',2,2,25000,'paper','ep:document','gallery/paper/index','GalleryPaper',0,b'1',b'1',b'1','admin'),
 (25003,'论文日志','',2,3,25000,'paper-log','ep:tickets','gallery/job/index','GalleryJob',0,b'1',b'1',b'1','admin'),
 (25004,'审核队列','',2,4,25000,'review','ep:finished','gallery/review/index','GalleryReview',0,b'1',b'1',b'1','admin'),
 (25005,'发布管理','',2,5,25000,'publish','ep:upload','gallery/publish/index','GalleryPublish',0,b'1',b'1',b'1','admin'),
 (25006,'管线设置','',2,6,25000,'setting','ep:setting','gallery/setting/index','GallerySetting',0,b'1',b'1',b'1','admin'),
 (25101,'论文查询','gallery:paper:query',3,1,25002,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25102,'论文更新','gallery:paper:update',3,2,25002,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25103,'论文删除','gallery:paper:delete',3,3,25002,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25104,'任务查询','gallery:job:query',3,1,25003,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25105,'任务创建','gallery:job:create',3,4,25002,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25106,'任务取消','gallery:job:cancel',3,5,25002,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25107,'审核查询','gallery:review:query',3,1,25004,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25108,'审核操作','gallery:review:approve',3,2,25004,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25109,'发布查询','gallery:publish:query',3,1,25005,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25110,'发布执行','gallery:publish:execute',3,2,25005,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25111,'参数查询','gallery:setting:query',3,1,25006,'','',NULL,NULL,0,b'1',b'1',b'1','admin'),
 (25112,'参数更新','gallery:setting:update',3,2,25006,'','',NULL,NULL,0,b'1',b'1',b'1','admin');

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`tenant_id`)
SELECT 1, id, 'admin', 1 FROM `system_menu` WHERE id BETWEEN 25000 AND 25199;
