package cn.iocoder.yudao.module.gallery.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 静态画廊图片（对应静态站 data/figures.json 中的一条记录）。
 */
@TableName("gallery_figure")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryFigureDO extends BaseDO {

    @TableId
    private Long id;
    /** 关联 gallery_paper.id（流水线发布的记录） */
    private Long paperId;
    /** 静态站图片 ID，例如 cvpr2025-123456 */
    private String figureId;
    /** 来源：IMPORT / PIPELINE / MANUAL */
    private String origin;
    /** 最后一次人工编辑时间；非空表示流水线发布不得覆盖 */
    private java.time.LocalDateTime manualEditedTime;
    /** 会议 */
    private String venue;
    private Integer year;
    private String title;
    /** 作者 JSON 数组 */
    private String authors;
    /** 视觉模式 */
    private String pattern;
    /** 等级：oral / spotlight */
    private String tier;
    /** 奖项：best / honorable */
    private String award;
    /** 图片相对路径，如 images/cvpr/final/cvpr2025-1.jpg */
    private String image;
    private String paper;
    private String pdfSource;
    private Double score;
    private Integer width;
    private Integer height;
    private String remark;
}
