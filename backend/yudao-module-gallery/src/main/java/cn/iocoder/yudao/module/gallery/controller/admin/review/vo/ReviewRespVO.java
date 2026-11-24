package cn.iocoder.yudao.module.gallery.controller.admin.review.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 审核项 Response VO")
@Data
public class ReviewRespVO {

    private Long id;
    private Long paperId;
    private Long jobId;
    private Long cropId;
    private String venue;
    private Integer year;
    private String title;
    private String authors;
    private String paperUrl;
    private Integer pageNo;
    private Integer dpi;
    private Integer width;
    private Integer height;
    private Double confidence;
    private Double quality;
    private String flagsJson;
    private String scoresJson;
    private String qcJson;
    private Integer status;
    private String reviewer;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;
    private String reviewComment;
    private String algorithm;
    /** base64 缩略图，便于 img 直接展示 */
    private String thumb;
    private String pattern;
    private String tier;
    private String award;
}
