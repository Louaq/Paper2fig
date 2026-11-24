package cn.iocoder.yudao.module.gallery.controller.admin.setting;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gallery.controller.admin.setting.vo.SettingUpdateReqVO;
import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 画廊管线参数")
@RestController
@RequestMapping("/gallery/setting")
@Validated
public class GallerySettingController {

    @Resource
    private GallerySettingService settingService;

    @GetMapping("/get")
    @Operation(summary = "获得全部参数")
    @PreAuthorize("@ss.hasPermission('gallery:setting:query')")
    public CommonResult<Map<String, String>> get() {
        return success(settingService.getAll());
    }

    @PutMapping("/update")
    @Operation(summary = "批量更新参数")
    @PreAuthorize("@ss.hasPermission('gallery:setting:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody SettingUpdateReqVO reqVO) {
        settingService.update(reqVO.getValues());
        return success(true);
    }
}
