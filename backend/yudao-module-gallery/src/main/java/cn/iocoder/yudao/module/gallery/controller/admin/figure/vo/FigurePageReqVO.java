package cn.iocoder.yudao.module.gallery.controller.admin.figure.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 静态画廊图片分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FigurePageReqVO extends PageParam {

    @Schema(description = "会议", example = "cvpr")
    private String venue;
    @Schema(description = "年份", example = "2025")
    private Integer year;
    @Schema(description = "等级：best/oral/spotlight")
    private String tier;
    @Schema(description = "视觉模式")
    private String pattern;
    @Schema(description = "关键词：标题/作者/图片 ID")
    private String keyword;
}
