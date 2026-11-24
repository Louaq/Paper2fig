package cn.iocoder.yudao.module.gallery.controller.admin.job.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 新建采集/裁剪任务 Request VO")
@Data
public class JobCreateReqVO {

    @Schema(description = "会议", requiredMode = Schema.RequiredMode.REQUIRED, example = "iclr")
    private String venue;
    @Schema(description = "年份", requiredMode = Schema.RequiredMode.REQUIRED, example = "2025")
    private Integer year;
    @Schema(description = "采集上限（0 表示全部）", example = "50")
    private Integer limit;
    @Schema(description = "裁剪上限（0 表示全部待处理论文）", example = "20")
    private Integer cropLimit;
    @Schema(description = "DPI，0 表示使用默认值", example = "200")
    private Integer dpi;
    @Schema(description = "是否包含图注")
    private Boolean includeCaption;
    @Schema(description = "解析方式：local（本地环境）/ mineru（在线 API）", example = "local")
    private String parseProvider;
    @Schema(description = "仅使用已下载 PDF，不再抓取")
    private Boolean noFetch;
    @Schema(description = "论文 ID 列表（裁剪任务可指定）")
    private java.util.List<Long> paperIds;
}
