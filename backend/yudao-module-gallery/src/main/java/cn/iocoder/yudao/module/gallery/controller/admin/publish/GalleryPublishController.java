package cn.iocoder.yudao.module.gallery.controller.admin.publish;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gallery.controller.admin.publish.vo.PublishPageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.publish.vo.PublishRespVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPublishDO;
import cn.iocoder.yudao.module.gallery.service.publish.GalleryPublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 画廊发布")
@RestController
@RequestMapping("/gallery/publish")
@Validated
public class GalleryPublishController {

    @Resource
    private GalleryPublishService publishService;

    @GetMapping("/page")
    @Operation(summary = "获得发布记录分页")
    @PreAuthorize("@ss.hasPermission('gallery:publish:query')")
    public CommonResult<PageResult<PublishRespVO>> page(@Valid PublishPageReqVO reqVO) {
        PageResult<GalleryPublishDO> page = publishService.getPublishPage(reqVO);
        return success(BeanUtils.toBean(page, PublishRespVO.class));
    }

    @GetMapping("/summary")
    @Operation(summary = "待发布概览")
    @PreAuthorize("@ss.hasPermission('gallery:publish:query')")
    public CommonResult<Map<String, Object>> summary() {
        return success(publishService.summary());
    }

    @PostMapping("/cleanup-artifacts")
    @Operation(summary = "清理已发布论文的原始 PDF / 裁剪图 / 发布目录")
    @PreAuthorize("@ss.hasPermission('gallery:publish:execute')")
    public CommonResult<Map<String, Object>> cleanupArtifacts(
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return success(publishService.cleanupArtifacts(dryRun));
    }

    @PostMapping("/preview")
    @Operation(summary = "发布预检（dry-run，不改线上文件）")
    @PreAuthorize("@ss.hasPermission('gallery:publish:execute')")
    public CommonResult<Map<String, Object>> preview(
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        return success(publishService.preview(force));
    }

    @PostMapping("/execute")
    @Operation(summary = "执行发布（写图片 + figures.json + figures.js）")
    @PreAuthorize("@ss.hasPermission('gallery:publish:execute')")
    public CommonResult<Map<String, Object>> execute(
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        return success(publishService.execute(force));
    }

    @PostMapping("/sync-search")
    @Operation(summary = "全量重建 Meilisearch 检索索引")
    @PreAuthorize("@ss.hasPermission('gallery:publish:execute')")
    public CommonResult<Map<String, Object>> syncSearch() {
        return success(publishService.syncSearchIndex());
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除发布记录")
    @PreAuthorize("@ss.hasPermission('gallery:publish:execute')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        publishService.deleteRecord(id);
        return success(true);
    }
}
