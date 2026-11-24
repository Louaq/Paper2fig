package cn.iocoder.yudao.module.gallery.controller.admin.paper.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 论文 Response VO")
@Data
public class PaperRespVO {

    private Long id;
    private String venue;
    private Integer year;
    private String source;
    private String sourceId;
    private String title;
    private List<String> authors;
    private String abstractText;
    private String paperUrl;
    private String pdfUrl;
    private String doi;
    private String arxivId;
    private String decision;
    private String track;
    private String pattern;
    private String tier;
    private String award;
    private Double score;
    private Integer status;
    private Integer pdfStatus;
    private Integer pdfPages;
    private Long pdfSize;
    private String pdfError;
    private LocalDateTime pdfTime;
    private Long cropId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 当前裁剪预览（base64 缩略图） */
    private String thumb;
    private Double confidence;
    private String flags;
    private Integer cropWidth;
    private Integer cropHeight;
}
