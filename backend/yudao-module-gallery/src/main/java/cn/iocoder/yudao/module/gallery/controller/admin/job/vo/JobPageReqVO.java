package cn.iocoder.yudao.module.gallery.controller.admin.job.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 任务分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class JobPageReqVO extends PageParam {

    @Schema(description = "类型：CRAWL/FETCH/CROP/PUBLISH")
    private String kind;
    @Schema(description = "只查论文相关任务（CRAWL/CROP）")
    private Boolean paperOnly;
    private Integer status;
    private String venue;
    private Integer year;
}
