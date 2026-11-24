package cn.iocoder.yudao.module.gallery.service.paper;

public interface GalleryImportService {

    /** 采集结果 -> 论文库，返回 [新增, 重复, 失败] */
    int[] importCrawl(Long jobId, String filePath);

    /** 裁剪结果 -> 裁剪表 + 论文状态，返回 [成功, 失败] */
    int[] importCrop(Long jobId, String filePath);

    /** 下载结果 -> 论文 PDF 状态 */
    int[] importFetch(Long jobId, String filePath);
}
