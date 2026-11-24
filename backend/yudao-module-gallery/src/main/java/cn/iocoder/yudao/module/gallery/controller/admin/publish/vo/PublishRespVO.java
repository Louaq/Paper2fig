package cn.iocoder.yudao.module.gallery.controller.admin.publish.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 发布记录 Response VO")
@Data
public class PublishRespVO {

    private Long id;
    private String version;
    private String siteDir;
    private Integer added;
    private Integer updated;
    private Integer total;
    private Integer status;
    private String backupDir;
    private String resultJson;
    private Long jobId;
    private String operatorName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
