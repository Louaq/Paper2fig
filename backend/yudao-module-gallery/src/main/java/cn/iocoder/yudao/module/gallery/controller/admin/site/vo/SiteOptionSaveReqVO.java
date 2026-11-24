package cn.iocoder.yudao.module.gallery.controller.admin.site.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 前端筛选项新增/修改 Request VO")
@Data
public class SiteOptionSaveReqVO {

    @Schema(description = "编号（修改时必填）")
    private Long id;

    @Schema(description = "维度：venue/year/tier/pattern", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "维度不能为空")
    private String dimension;

    @Schema(description = "选项值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "选项值不能为空")
    private String optionValue;

    private String labelZh;
    private String labelEn;

    @Schema(description = "排序（升序）")
    @NotNull(message = "排序不能为空")
    private Integer sort;
}
