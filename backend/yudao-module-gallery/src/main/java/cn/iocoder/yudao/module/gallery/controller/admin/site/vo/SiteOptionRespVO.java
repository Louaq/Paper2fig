package cn.iocoder.yudao.module.gallery.controller.admin.site.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 前端筛选项 Response VO")
@Data
public class SiteOptionRespVO {

    private Long id;
    /** venue / year / tier / pattern */
    private String dimension;
    private String optionValue;
    private String labelZh;
    private String labelEn;
    private Integer sort;
}
