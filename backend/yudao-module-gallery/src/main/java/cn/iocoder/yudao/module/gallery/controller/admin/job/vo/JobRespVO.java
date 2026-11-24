package cn.iocoder.yudao.module.gallery.controller.admin.job.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 任务 Response VO")
@Data
public class JobRespVO {

    private Long id;
    private String kind;
    private String venue;
    private Integer year;
    private Integer status;
    private Integer progress;
    private Integer total;
    private Integer done;
    private Integer failed;
    private Long pid;
    private String errorMsg;
    private String paramsJson;
    private String resultJson;
    private String logPath;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private Long durationSeconds;
}
