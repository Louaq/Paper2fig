package cn.iocoder.yudao.module.gallery.service.artifacts;

import java.util.Collection;
import java.util.Map;

public interface GalleryArtifactCleanupService {

    /** 清理所有已发布论文的原始 PDF、工作区裁剪图、发布导出目录/日志。 */
    Map<String, Object> cleanupPublished(boolean dryRun);

    /** 发布成功后立即清理本批次论文的原始文件。 */
    Map<String, Object> cleanupAfterPublish(Collection<Long> paperIds);
}
