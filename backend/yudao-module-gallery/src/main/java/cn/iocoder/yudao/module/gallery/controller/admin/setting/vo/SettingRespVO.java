package cn.iocoder.yudao.module.gallery.controller.admin.setting.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 管线参数 Response VO")
@Data
public class SettingRespVO {

    private String configKey;
    private String configValue;
    private String remark;
}
