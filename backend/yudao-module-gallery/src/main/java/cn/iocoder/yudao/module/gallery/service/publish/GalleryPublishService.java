package cn.iocoder.yudao.module.gallery.service.publish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gallery.controller.admin.publish.vo.PublishPageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPublishDO;

import java.util.Map;

public interface GalleryPublishService {

    PageResult<GalleryPublishDO> getPublishPage(PublishPageReqVO reqVO);

    /** 预检：只写临时清单并跑 dry-run，不改线上文件 */
    Map<String, Object> preview(boolean force);

    /** 实际发布：拷图 + 合并 figures.json + 重新生成 figures.js */
    Map<String, Object> execute(boolean force);

    /** 当前待发布数量与阻塞问题 */
    Map<String, Object> summary();

    /** 清理已发布论文的原始 PDF / 裁剪图 / 发布目录（dryRun 只统计）。 */
    Map<String, Object> cleanupArtifacts(boolean dryRun);

    /** 删除一条发布记录（仅记录，不影响已发布静态站） */
    void deleteRecord(Long id);

    /** 用当前已发布目录全量重建 Meilisearch 检索索引。 */
    Map<String, Object> syncSearchIndex();
}
