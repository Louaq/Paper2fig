package cn.iocoder.yudao.module.gallery.service.crop;

import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryCropDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPaperDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryCropMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryPaperMapper;
import cn.iocoder.yudao.module.gallery.enums.GalleryConstants;
import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.CROP_DELETE_FAILED;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.CROP_NOT_EXISTS;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.CROP_NOT_PENDING;

@Service
@Validated
@Slf4j
public class GalleryCropServiceImpl implements GalleryCropService {

    @Resource
    private GalleryCropMapper cropMapper;
    @Resource
    private GalleryPaperMapper paperMapper;
    @Resource
    private GallerySettingService settingService;

    @Override
    public GalleryCropDO validateCropExists(Long id) {
        GalleryCropDO crop = cropMapper.selectById(id);
        if (crop == null) {
            throw exception(CROP_NOT_EXISTS);
        }
        return crop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, ReviewReqVO reqVO, String reviewer) {
        GalleryCropDO crop = validateCropExists(id);
        if (!Objects.equals(crop.getStatus(), GalleryConstants.CROP_PENDING)) {
            throw exception(CROP_NOT_PENDING);
        }
        GalleryCropDO update = new GalleryCropDO();
        update.setId(id);
        update.setStatus(GalleryConstants.CROP_APPROVED);
        update.setReviewer(reviewer);
        update.setReviewTime(LocalDateTime.now());
        update.setReviewComment(reqVO.getComment());
        cropMapper.updateById(update);

        GalleryPaperDO paper = new GalleryPaperDO();
        paper.setId(crop.getPaperId());
        paper.setStatus(GalleryConstants.PAPER_APPROVED);
        paper.setPattern(reqVO.getPattern() == null || reqVO.getPattern().isBlank() ? "teaser" : reqVO.getPattern());
        paper.setTier(reqVO.getTier());
        paper.setAward(reqVO.getAward());
        paperMapper.updateById(paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String comment, String reviewer) {
        GalleryCropDO crop = validateCropExists(id);
        if (!Objects.equals(crop.getStatus(), GalleryConstants.CROP_PENDING)) {
            throw exception(CROP_NOT_PENDING);
        }
        GalleryCropDO update = new GalleryCropDO();
        update.setId(id);
        update.setStatus(GalleryConstants.CROP_REJECTED);
        update.setReviewer(reviewer);
        update.setReviewTime(LocalDateTime.now());
        update.setReviewComment(comment);
        cropMapper.updateById(update);

        GalleryPaperDO paper = new GalleryPaperDO();
        paper.setId(crop.getPaperId());
        paper.setStatus(GalleryConstants.PAPER_REJECTED);
        paperMapper.updateById(paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchApprove(List<Long> ids, String pattern, String reviewer) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            ReviewReqVO req = new ReviewReqVO();
            req.setPattern(pattern);
            approve(id, req, reviewer);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requeue(Long id, String reviewer) {
        GalleryCropDO crop = validateCropExists(id);
        GalleryCropDO update = new GalleryCropDO();
        update.setId(id);
        update.setStatus(GalleryConstants.CROP_REJECTED);
        update.setReviewer(reviewer);
        update.setReviewTime(LocalDateTime.now());
        update.setReviewComment("重新裁剪");
        cropMapper.updateById(update);

        GalleryPaperDO paper = new GalleryPaperDO();
        paper.setId(crop.getPaperId());
        paper.setStatus(GalleryConstants.PAPER_NEW);
        paperMapper.updateById(paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCrop(Long id, String reviewer) {
        GalleryCropDO crop = validateCropExists(id);
        deleteImageFile(crop.getImagePath());

        GalleryPaperDO paper = paperMapper.selectById(crop.getPaperId());
        cropMapper.clearImagePath(id);
        cropMapper.deleteById(id);
        if (paper != null && Objects.equals(paper.getCropId(), id)) {
            // 已发布的论文：静态站图片已独立保存，只解除裁剪引用，保持已发布状态；
            // 其余状态回到待处理，方便重新裁剪。
            Integer status = Objects.equals(paper.getStatus(), GalleryConstants.PAPER_PUBLISHED)
                    ? GalleryConstants.PAPER_PUBLISHED : GalleryConstants.PAPER_NEW;
            paperMapper.resetCrop(paper.getId(), status);
        }
        log.info("[deleteCrop][{}] 删除裁剪图，操作人：{}", id, reviewer);
    }

    /** 只删除工作区内的裁剪图文件，并顺带清理空目录。 */
    private void deleteImageFile(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }
        Path cropsRoot = Path.of(settingService.workdir(), "workspace", "crops")
                .toAbsolutePath().normalize();
        Path image = Path.of(imagePath).toAbsolutePath().normalize();
        if (!image.startsWith(cropsRoot)) {
            log.warn("[deleteCrop] 跳过工作区外的图片：{}", image);
            return;
        }
        try {
            Files.deleteIfExists(image);
            Path parent = image.getParent();
            if (parent != null && !parent.equals(cropsRoot) && Files.isDirectory(parent)) {
                try (var entries = Files.list(parent)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[deleteCrop] 删除图片失败 {}：{}", image, e.getMessage());
            throw exception(CROP_DELETE_FAILED);
        }
    }
}
