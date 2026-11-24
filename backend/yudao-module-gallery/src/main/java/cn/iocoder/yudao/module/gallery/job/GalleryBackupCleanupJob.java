package cn.iocoder.yudao.module.gallery.job;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.module.gallery.service.backup.GalleryBackupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 每日备份清理：按 backup_keep_days / keep_min / keep_max / archive_days 清理备份目录。
 * 处理器名字 galleryBackupCleanupJob，参数可留空。
 */
@Component
@Slf4j
public class GalleryBackupCleanupJob implements JobHandler {

    @Resource
    private GalleryBackupService backupService;

    @Override
    public String execute(String param) {
        Map<String, Object> result = backupService.cleanup(false);
        log.info("[execute] 备份清理完成：{}", result);
        return "备份清理：删除 " + result.get("removed") + " 份，归档 " + result.get("archived")
                + " 份，释放 " + result.get("freed");
    }
}
