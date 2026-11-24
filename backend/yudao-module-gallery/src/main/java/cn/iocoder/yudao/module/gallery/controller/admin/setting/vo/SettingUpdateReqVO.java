package cn.iocoder.yudao.module.gallery.controller.admin.setting.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 管线参数更新 Request VO")
@Data
public class SettingUpdateReqVO {

    @NotEmpty(message = "参数不能为空")
    private Map<String, String> values;
}
