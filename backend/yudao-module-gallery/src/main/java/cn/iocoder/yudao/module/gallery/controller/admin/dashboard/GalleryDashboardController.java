package cn.iocoder.yudao.module.gallery.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gallery.service.paper.GalleryPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 画廊数据看板")
@RestController
@RequestMapping("/gallery/dashboard")
public class GalleryDashboardController {

    @Resource
    private GalleryPaperService paperService;

    @GetMapping("/stats")
    @Operation(summary = "看板统计")
    @PreAuthorize("@ss.hasPermission('gallery:paper:query')")
    public CommonResult<Map<String, Object>> stats() {
        return success(paperService.getDashboard());
    }
}
