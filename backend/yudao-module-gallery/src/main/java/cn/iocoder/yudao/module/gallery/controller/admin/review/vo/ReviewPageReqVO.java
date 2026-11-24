package cn.iocoder.yudao.module.gallery.controller.admin.review.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 审核队列分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewPageReqVO extends PageParam {

    @Schema(description = "审核状态：0 待审 1 通过 2 拒绝")
    private Integer status;
    private Long paperId;
    private Long jobId;
    private Double minConfidence;
    private Double maxConfidence;
}
