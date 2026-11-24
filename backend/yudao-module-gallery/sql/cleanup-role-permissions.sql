-- 角色权限清理（2026-11-23）
-- 1) 移除已隐藏/不再使用的「数据看板」菜单及其权限点
DELETE FROM `system_role_menu` WHERE `menu_id` IN (25001, 25113);
DELETE FROM `system_menu` WHERE `id` IN (25001, 25113);

-- 2) 数据权限只保留「全部数据权限(1)」和「仅本人数据权限(5)」
--    系统已无部门概念，部门相关范围不再提供
UPDATE `system_role` SET `data_scope` = 1 WHERE `data_scope` NOT IN (1, 5);
DELETE FROM `system_dict_data`
 WHERE `dict_type` = 'system_data_scope' AND `value` IN (2, 3, 4);
