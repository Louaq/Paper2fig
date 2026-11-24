package cn.iocoder.yudao.module.gallery.controller.admin.paper.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 论文更新 Request VO")
@Data
public class PaperUpdateReqVO {

    @NotNull(message = "编号不能为空")
    private Long id;
    private String title;
    private String pattern;
    private String tier;
    private String award;
    private Double score;
    private String remark;
}
