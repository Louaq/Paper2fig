package cn.iocoder.yudao.module.gallery.service.backup;

import java.util.Map;

public interface GalleryBackupService {

    /** 备份目录、数量、占用空间。 */
    Map<String, Object> summary();

    /** 按保留策略清理备份；dryRun=true 只返回将要执行的动作。 */
    Map<String, Object> cleanup(boolean dryRun);
}
