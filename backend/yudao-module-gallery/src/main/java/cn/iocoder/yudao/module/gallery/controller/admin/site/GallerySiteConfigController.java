package cn.iocoder.yudao.module.gallery.controller.admin.site;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gallery.controller.admin.site.vo.SiteOptionRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.site.vo.SiteOptionSaveReqVO;
import cn.iocoder.yudao.module.gallery.service.site.GallerySiteConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 静态画廊前端配置")
@RestController
@RequestMapping("/gallery/site-config")
@Validated
public class GallerySiteConfigController {

    @Resource
    private GallerySiteConfigService siteConfigService;

    @GetMapping("/list")
    @Operation(summary = "获得筛选项列表")
    @Parameter(name = "dimension", description = "维度：venue/year/tier/pattern，不传返回全部")
    @PreAuthorize("@ss.hasAnyPermissions('gallery:site-config:query', 'gallery:figure:query')")
    public CommonResult<List<SiteOptionRespVO>> list(@RequestParam(value = "dimension", required = false) String dimension) {
        return success(siteConfigService.getOptionList(dimension));
    }

    @PostMapping("/create")
    @Operation(summary = "新增筛选项")
    @PreAuthorize("@ss.hasPermission('gallery:site-config:update')")
    public CommonResult<Long> create(@Valid @RequestBody SiteOptionSaveReqVO reqVO) {
        return success(siteConfigService.createOption(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改筛选项")
    @PreAuthorize("@ss.hasPermission('gallery:site-config:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody SiteOptionSaveReqVO reqVO) {
        siteConfigService.updateOption(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除筛选项")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('gallery:site-config:update')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        siteConfigService.deleteOption(id);
        return success(true);
    }

    @PutMapping("/sort")
    @Operation(summary = "按拖拽顺序重排筛选项")
    @PreAuthorize("@ss.hasPermission('gallery:site-config:update')")
    public CommonResult<Boolean> sort(@RequestBody List<Long> ids) {
        siteConfigService.sortOptions(ids);
        return success(true);
    }
}
