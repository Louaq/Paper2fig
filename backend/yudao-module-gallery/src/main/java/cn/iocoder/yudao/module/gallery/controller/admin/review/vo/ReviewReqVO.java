package cn.iocoder.yudao.module.gallery.controller.admin.review.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 审核操作 Request VO")
@Data
public class ReviewReqVO {

    private Long id;
    @Schema(description = "通过的视觉模式（通过时必填）")
    private String pattern;
    private String tier;
    private String award;
    private String comment;
    @Schema(description = "批量操作")
    private List<Long> ids;
}
