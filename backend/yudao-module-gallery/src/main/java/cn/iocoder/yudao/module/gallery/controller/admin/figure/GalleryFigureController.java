package cn.iocoder.yudao.module.gallery.controller.admin.figure;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigurePageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigureRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigureUpdateReqVO;
import cn.iocoder.yudao.module.gallery.service.figure.GalleryFigureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 静态画廊图片")
@RestController
@RequestMapping("/gallery/figure")
@Validated
public class GalleryFigureController {

    @Resource
    private GalleryFigureService figureService;

    @GetMapping("/page")
    @Operation(summary = "获得图片分页")
    @PreAuthorize("@ss.hasPermission('gallery:figure:query')")
    public CommonResult<PageResult<FigureRespVO>> getFigurePage(@Valid FigurePageReqVO reqVO) {
        return success(figureService.getFigurePage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得图片详情")
    @Parameter(name = "id", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('gallery:figure:query')")
    public CommonResult<FigureRespVO> getFigure(@RequestParam("id") Long id) {
        return success(figureService.getFigure(id));
    }

    @PutMapping("/update")
    @Operation(summary = "更新图片属性")
    @PreAuthorize("@ss.hasPermission('gallery:figure:update')")
    public CommonResult<Boolean> updateFigure(@Valid @RequestBody FigureUpdateReqVO reqVO) {
        figureService.updateFigure(reqVO);
        return success(true);
    }

    @PostMapping("/upload-image")
    @Operation(summary = "替换图片文件")
    @PreAuthorize("@ss.hasPermission('gallery:figure:update')")
    public CommonResult<Map<String, Integer>> uploadImage(@RequestParam("id") Long id,
                                                          @RequestParam("file") MultipartFile file) {
        int[] size = figureService.replaceImage(id, file);
        return success(Map.of("width", size[0], "height", size[1]));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除图片")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('gallery:figure:delete')")
    public CommonResult<Boolean> deleteFigure(@RequestParam("id") Long id) {
        figureService.deleteFigure(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除图片")
    @PreAuthorize("@ss.hasPermission('gallery:figure:delete')")
    public CommonResult<Boolean> deleteFigureList(@RequestParam("ids") List<Long> ids) {
        ids.forEach(figureService::deleteFigure);
        return success(true);
    }
}
