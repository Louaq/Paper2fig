package cn.iocoder.yudao.module.gallery.service.figure;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigurePageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigureRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigureUpdateReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryFigureDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryFilterOptionDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryFigureMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryFilterOptionMapper;
import cn.iocoder.yudao.module.gallery.service.site.GallerySiteFileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.FIGURE_NOT_EXISTS;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.OPTION_INVALID;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.VENUE_INVALID;

@Service
@Validated
@Slf4j
public class GalleryFigureServiceImpl implements GalleryFigureService {

    @Resource
    private GalleryFigureMapper figureMapper;
    @Resource
    private GalleryFilterOptionMapper optionMapper;
    @Resource
    private GallerySiteFileService siteFileService;

    @Override
    public PageResult<FigureRespVO> getFigurePage(FigurePageReqVO reqVO) {
        PageResult<GalleryFigureDO> page = figureMapper.selectPage(reqVO);
        List<FigureRespVO> list = new ArrayList<>(page.getList().size());
        for (GalleryFigureDO figure : page.getList()) {
            list.add(toRespVO(figure));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public FigureRespVO getFigure(Long id) {
        return toRespVO(validateFigureExists(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFigure(FigureUpdateReqVO reqVO) {
        GalleryFigureDO figure = validateFigureExists(reqVO.getId());
        validateVenue(reqVO.getVenue());
        validateOption("pattern", reqVO.getPattern());
        validateOption("tier", reqVO.getTier());
        if ("best".equals(reqVO.getTier())) {
            // “最佳论文”属于奖项，不写入 tier
            throw exception(OPTION_INVALID);
        }

        List<String> authors = reqVO.getAuthors() == null ? List.of() : reqVO.getAuthors().stream()
                .map(item -> item == null ? "" : item.trim()).filter(item -> !item.isEmpty()).toList();
        GalleryFigureDO update = new GalleryFigureDO();
        update.setId(figure.getId());
        update.setTitle(reqVO.getTitle().trim());
        update.setAuthors(JsonUtils.toJsonString(authors));
        update.setVenue(reqVO.getVenue().toLowerCase());
        update.setYear(reqVO.getYear());
        update.setPattern(blankToNull(reqVO.getPattern()));
        update.setTier(blankToNull(reqVO.getTier()));
        update.setAward(blankToNull(reqVO.getAward()));
        update.setPaper(blankToNull(reqVO.getPaper()));
        update.setPdfSource(blankToNull(reqVO.getPdfSource()));
        update.setScore(reqVO.getScore());
        update.setRemark(blankToNull(reqVO.getRemark()));
        // 人工编辑优先：流水线发布不得覆盖
        update.setOrigin("MANUAL");
        update.setManualEditedTime(java.time.LocalDateTime.now());
        figureMapper.updateAllFields(update);
        siteFileService.renderSite();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int[] replaceImage(Long id, MultipartFile file) {
        GalleryFigureDO figure = validateFigureExists(id);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }
        Path backupDir = siteFileService.backup();
        int[] size = siteFileService.replaceImage(figure.getImage(), content, backupDir);
        figureMapper.updateImageAndMarkManual(figure.getId(), size[0], size[1]);
        siteFileService.renderSite(backupDir);
        return size;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFigure(Long id) {
        GalleryFigureDO figure = validateFigureExists(id);
        Path backupDir = siteFileService.backup();
        figureMapper.deletePhysicalById(id);
        // 先从目录中移除并渲染成功，再删图片文件；渲染失败时事务回滚且文件可恢复
        siteFileService.renderSite(backupDir);
        siteFileService.deleteImage(figure.getImage(), backupDir);
    }

    // ------------------------------------------------------------------
    private FigureRespVO toRespVO(GalleryFigureDO figure) {
        FigureRespVO vo = BeanUtils.toBean(figure, FigureRespVO.class);
        vo.setAuthors(JsonUtils.parseArray(figure.getAuthors() == null ? "[]" : figure.getAuthors(), String.class));
        vo.setImageUrl(figure.getImage() == null || figure.getImage().isEmpty() ? null : "/" + figure.getImage());
        return vo;
    }

    private GalleryFigureDO validateFigureExists(Long id) {
        GalleryFigureDO figure = figureMapper.selectById(id);
        if (figure == null) {
            throw exception(FIGURE_NOT_EXISTS);
        }
        return figure;
    }

    private void validateVenue(String venue) {
        if (venue == null || optionMapper.selectByDimensionAndValue("venue", venue.toLowerCase()) == null) {
            throw exception(VENUE_INVALID);
        }
    }

    private void validateOption(String dimension, String value) {
        if (value != null && !value.isBlank()
                && optionMapper.selectByDimensionAndValue(dimension, value) == null) {
            throw exception(OPTION_INVALID);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
