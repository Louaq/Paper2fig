package cn.iocoder.yudao.module.gallery.controller.admin.figure.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 静态画廊图片更新 Request VO")
@Data
public class FigureUpdateReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "标题不能为空")
    private String title;

    private List<String> authors;

    @Schema(description = "会议", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会议不能为空")
    private String venue;

    @Schema(description = "年份", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "年份不能为空")
    private Integer year;

    private String pattern;
    /** oral / spotlight；空表示无等级 */
    private String tier;
    /** best / honorable；空表示无奖项 */
    private String award;
    private String paper;
    private String pdfSource;
    private Double score;
    private String remark;
}
