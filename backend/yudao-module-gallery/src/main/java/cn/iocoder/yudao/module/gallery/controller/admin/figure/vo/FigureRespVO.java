package cn.iocoder.yudao.module.gallery.controller.admin.figure.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 静态画廊图片 Response VO")
@Data
public class FigureRespVO {

    private Long id;
    private Long paperId;
    private String figureId;
    private String origin;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime manualEditedTime;
    private String venue;
    private Integer year;
    private String title;
    private List<String> authors;
    private String pattern;
    private String tier;
    private String award;
    private String image;
    /** 图片可访问地址（拼接静态站域名） */
    private String imageUrl;
    private String paper;
    private String pdfSource;
    private Double score;
    private Integer width;
    private Integer height;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
