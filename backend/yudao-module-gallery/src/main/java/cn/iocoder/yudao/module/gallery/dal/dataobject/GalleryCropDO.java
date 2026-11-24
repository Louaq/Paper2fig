package cn.iocoder.yudao.module.gallery.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 一次裁剪的产物与质检结论（保留坐标/算法版本，方便重裁与追溯）。
 */
@TableName("gallery_crop")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryCropDO extends BaseDO {

    @TableId
    private Long id;
    private Long paperId;
    private Long jobId;
    private Integer pageNo;
    /** PDF 坐标 x0,y0,x1,y1 */
    private String rect;
    private Integer dpi;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private String imagePath;
    private Double confidence;
    private Double quality;
    private String scoresJson;
    private String flagsJson;
    private String qcJson;
    private String phash;
    /** 0 待审核 1 通过 2 拒绝 */
    private Integer status;
    private String reviewer;
    private java.time.LocalDateTime reviewTime;
    private String reviewComment;
    private String algorithm;
    private String diagnostics;
}
