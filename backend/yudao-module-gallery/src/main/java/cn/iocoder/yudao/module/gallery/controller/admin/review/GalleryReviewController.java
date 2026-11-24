package cn.iocoder.yudao.module.gallery.controller.admin.review;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewPageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewRespVO;
import cn.iocoder.yudao.module.gallery.service.crop.GalleryCropService;
import cn.iocoder.yudao.module.gallery.service.paper.GalleryPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 画廊审核队列")
@RestController
@RequestMapping("/gallery/review")
@Validated
public class GalleryReviewController {

    @Resource
    private GalleryPaperService paperService;
    @Resource
    private GalleryCropService cropService;

    @GetMapping("/page")
    @Operation(summary = "获得审核队列分页")
    @PreAuthorize("@ss.hasPermission('gallery:review:query')")
    public CommonResult<PageResult<ReviewRespVO>> page(@Valid ReviewPageReqVO reqVO) {
        return success(paperService.getReviewPage(reqVO));
    }

    @PostMapping("/approve")
    @Operation(summary = "审核通过")
    @PreAuthorize("@ss.hasPermission('gallery:review:approve')")
    public CommonResult<Boolean> approve(@Valid @RequestBody ReviewReqVO reqVO) {
        cropService.approve(reqVO.getId(), reqVO, reviewer());
        return success(true);
    }

    @PostMapping("/reject")
    @Operation(summary = "审核拒绝")
    @PreAuthorize("@ss.hasPermission('gallery:review:approve')")
    public CommonResult<Boolean> reject(@Valid @RequestBody ReviewReqVO reqVO) {
        cropService.reject(reqVO.getId(), reqVO.getComment(), reviewer());
        return success(true);
    }

    @PostMapping("/batch-approve")
    @Operation(summary = "批量通过")
    @PreAuthorize("@ss.hasPermission('gallery:review:approve')")
    public CommonResult<Integer> batchApprove(@Valid @RequestBody ReviewReqVO reqVO) {
        cropService.batchApprove(reqVO.getIds(), reqVO.getPattern(), reviewer());
        return success(reqVO.getIds() == null ? 0 : reqVO.getIds().size());
    }

    @PostMapping("/requeue")
    @Operation(summary = "打回重新裁剪")
    @PreAuthorize("@ss.hasPermission('gallery:review:approve')")
    public CommonResult<Boolean> requeue(@Valid @RequestBody ReviewReqVO reqVO) {
        cropService.requeue(reqVO.getId(), reviewer());
        return success(true);
    }

    @DeleteMapping("/delete-crop")
    @Operation(summary = "手动删除裁剪图片")
    @PreAuthorize("@ss.hasPermission('gallery:review:approve')")
    public CommonResult<Boolean> deleteCrop(@RequestParam("id") Long id) {
        cropService.deleteCrop(id, reviewer());
        return success(true);
    }

    private String reviewer() {
        String nickname = SecurityFrameworkUtils.getLoginUserNickname();
        return nickname == null ? "system" : nickname;
    }
}
