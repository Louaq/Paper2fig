package cn.iocoder.yudao.module.gallery.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 论文记录（统一来源：OpenReview / PMLR / NeurIPS / CVF / ACL / AAAI / arXiv）。
 */
@TableName("gallery_paper")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryPaperDO extends BaseDO {

    @TableId
    private Long id;
    /** 会议：iclr/icml/neurips/cvpr/acl/aaai */
    private String venue;
    private Integer year;
    /** 来源适配器 */
    private String source;
    /** 来源侧论文 ID（OpenReview note id、PMLR 文件名、DOI 后缀…） */
    private String sourceId;
    /** 去重键，source:ID / doi:… / arxiv:… / title:… 用 | 拼接 */
    private String dedupeKey;
    private String title;
    /** 归一化标题，用于模糊去重 */
    private String titleNorm;
    /** JSON 数组 */
    private String authors;
    private String abstractText;
    private String paperUrl;
    private String pdfUrl;
    private String doi;
    private String arxivId;
    /** oral/spotlight/poster */
    private String decision;
    private String track;
    /** 视觉模式：teaser/conceptual/framework/pipeline/architecture/taxonomy */
    private String pattern;
    /** oral/spotlight */
    private String tier;
    /** best/honorable */
    private String award;
    private Double score;

    /** 处理状态，见 GalleryConstants.PAPER_* */
    private Integer status;
    /** PDF 缓存状态 */
    private Integer pdfStatus;
    private String pdfPath;
    private Integer pdfPages;
    private Long pdfSize;
    private String pdfError;
    private java.time.LocalDateTime pdfTime;
    /** 当前采用的裁剪结果 */
    private Long cropId;
    private java.time.LocalDateTime publishedTime;
    private String remark;
}
