package cn.iocoder.yudao.module.gallery.controller.admin.paper;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperPageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperUpdateReqVO;
import cn.iocoder.yudao.module.gallery.service.paper.GalleryPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 画廊论文库")
@RestController
@RequestMapping("/gallery/paper")
@Validated
public class GalleryPaperController {

    @Resource
    private GalleryPaperService paperService;

    @GetMapping("/page")
    @Operation(summary = "获得论文分页")
    @PreAuthorize("@ss.hasPermission('gallery:paper:query')")
    public CommonResult<PageResult<PaperRespVO>> getPaperPage(@Valid PaperPageReqVO reqVO) {
        return success(paperService.getPaperPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得论文详情")
    @Parameter(name = "id", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('gallery:paper:query')")
    public CommonResult<PaperRespVO> getPaper(@RequestParam("id") Long id) {
        return success(paperService.getPaper(id));
    }

    @PutMapping("/update")
    @Operation(summary = "更新论文标注（视觉模式/等级/评分）")
    @PreAuthorize("@ss.hasPermission('gallery:paper:update')")
    public CommonResult<Boolean> updatePaper(@Valid @RequestBody PaperUpdateReqVO reqVO) {
        paperService.updatePaper(reqVO);
        return success(true);
    }

    @PostMapping("/upload")
    @Operation(summary = "手动上传论文 PDF")
    @PreAuthorize("@ss.hasPermission('gallery:job:create')")
    public CommonResult<Long> uploadPdf(@RequestParam("file") MultipartFile file,
                                        @RequestParam("venue") String venue,
                                        @RequestParam("year") Integer year,
                                        @RequestParam(value = "title", required = false) String title,
                                        @RequestParam(value = "authors", required = false) String authors,
                                        @RequestParam(value = "doi", required = false) String doi)
            throws IOException {
        return success(paperService.uploadPdf(file, venue, year, title, authors, doi));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除论文")
    @PreAuthorize("@ss.hasPermission('gallery:paper:delete')")
    public CommonResult<Boolean> deletePaper(@RequestParam("id") Long id) {
        paperService.deletePaper(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除论文")
    @PreAuthorize("@ss.hasPermission('gallery:paper:delete')")
    public CommonResult<Boolean> deletePaperList(@RequestParam("ids") List<Long> ids) {
        ids.forEach(paperService::deletePaper);
        return success(true);
    }

    @DeleteMapping("/all")
    @Operation(summary = "清空论文库（论文记录、PDF、裁剪图片）")
    @PreAuthorize("@ss.hasPermission('gallery:paper:delete')")
    public CommonResult<java.util.Map<String, Object>> clearAllPapers() {
        return success(paperService.clearAllPapers());
    }

}
