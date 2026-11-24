package cn.iocoder.yudao.module.gallery.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 画廊业务常量：任务类型/状态、论文状态、裁剪审核状态。
 */
public interface GalleryConstants {

    // ---------- 任务类型 ----------
    String JOB_CRAWL = "CRAWL";       // 采集论文元数据
    String JOB_FETCH = "FETCH";       // 下载 PDF
    String JOB_CROP = "CROP";         // 裁剪 + 质检
    String JOB_PUBLISH = "PUBLISH";   // 合并到静态站
    List<String> JOB_KINDS = Arrays.asList(JOB_CRAWL, JOB_FETCH, JOB_CROP, JOB_PUBLISH);

    // ---------- 任务状态 ----------
    int JOB_WAITING = 0;
    int JOB_RUNNING = 1;
    int JOB_SUCCESS = 2;
    int JOB_FAILED = 3;
    int JOB_CANCELED = 4;

    // ---------- 会议 ----------
    // 采集/裁剪/发布共用的会议白名单，新增会议时只改这里
    List<String> VENUES = Arrays.asList(
            "iclr", "icml", "neurips", "cvpr", "acl", "aaai",
            "eccv", "miccai", "ijcai", "icme", "mm");

    // ---------- 论文状态 ----------
    int PAPER_NEW = 0;        // 已发现，未取 PDF
    int PAPER_CROPPED = 1;    // 已裁剪，待审核
    int PAPER_APPROVED = 2;   // 审核通过，待发布
    int PAPER_REJECTED = 3;   // 审核拒绝
    int PAPER_PUBLISHED = 4;  // 已发布

    // ---------- PDF 状态 ----------
    int PDF_NONE = 0;
    int PDF_CACHED = 1;
    int PDF_FAILED = 2;

    // ---------- 裁剪审核状态 ----------
    int CROP_PENDING = 0;
    int CROP_APPROVED = 1;
    int CROP_REJECTED = 2;

    // ---------- 发布状态 ----------
    int PUBLISH_PREVIEW = 0;
    int PUBLISH_SUCCESS = 1;
    int PUBLISH_FAILED = 2;
}
