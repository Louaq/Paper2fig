package cn.iocoder.yudao.module.gallery.controller.admin.backup;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gallery.service.backup.GalleryBackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 静态站备份")
@RestController
@RequestMapping("/gallery/backup")
@Validated
public class GalleryBackupController {

    @Resource
    private GalleryBackupService backupService;

    @GetMapping("/summary")
    @Operation(summary = "备份目录概览")
    @PreAuthorize("@ss.hasPermission('gallery:setting:query')")
    public CommonResult<Map<String, Object>> summary() {
        return success(backupService.summary());
    }

    @PostMapping("/cleanup")
    @Operation(summary = "按保留策略清理备份")
    @Parameter(name = "dryRun", description = "true 时只返回将要执行的动作")
    @PreAuthorize("@ss.hasPermission('gallery:setting:update')")
    public CommonResult<Map<String, Object>> cleanup(
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return success(backupService.cleanup(dryRun));
    }
}
