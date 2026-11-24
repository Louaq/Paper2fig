package cn.iocoder.yudao.module.gallery.service.search;

import java.util.List;
import java.util.Map;

/**
 * 画廊静态站的 Meilisearch 检索索引同步。
 *
 * 索引只用于前台关键词检索（/search 反代），失败不影响发布主流程。
 */
public interface GallerySearchIndexService {

    /** 用 gallery_figure 全量重建索引（发布后、手动重建时调用）。 */
    Map<String, Object> syncAll();

    /** 把给定记录写入索引（按 id 覆盖）。 */
    Map<String, Object> upsert(List<Map<String, Object>> records);
}
