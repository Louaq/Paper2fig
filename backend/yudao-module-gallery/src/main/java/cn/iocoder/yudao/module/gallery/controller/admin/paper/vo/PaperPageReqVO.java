package cn.iocoder.yudao.module.gallery.controller.admin.paper.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 论文分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class PaperPageReqVO extends PageParam {

    @Schema(description = "会议", example = "iclr")
    private String venue;
    @Schema(description = "年份", example = "2025")
    private Integer year;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "是否包含已发布论文")
    private Boolean includePublished;
    @Schema(description = "PDF 状态")
    private Integer pdfStatus;
    @Schema(description = "视觉模式")
    private String pattern;
    @Schema(description = "等级")
    private String tier;
    @Schema(description = "关键词：标题/作者/来源 ID/DOI")
    private String keyword;
    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;
}
