package cn.iocoder.yudao.module.gallery.service.site;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gallery.controller.admin.site.vo.SiteOptionRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.site.vo.SiteOptionSaveReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryFilterOptionDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryFilterOptionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.OPTION_EXISTS;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.OPTION_INVALID;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.OPTION_NOT_EXISTS;

@Service
@Validated
@Slf4j
public class GallerySiteConfigServiceImpl implements GallerySiteConfigService {

    private static final Set<String> DIMENSIONS = Set.of("venue", "year", "tier", "pattern");
    private static final Pattern VALUE_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("^\\d{4}$");

    @Resource
    private GalleryFilterOptionMapper optionMapper;
    @Resource
    private GallerySiteFileService siteFileService;

    @Override
    public List<SiteOptionRespVO> getOptionList(String dimension) {
        List<GalleryFilterOptionDO> list = dimension == null || dimension.isEmpty()
                ? optionMapper.selectListOrdered()
                : optionMapper.selectListByDimension(dimension);
        return list.stream().map(item -> BeanUtils.toBean(item, SiteOptionRespVO.class)).toList();
    }

    @Override
    public List<SiteOptionRespVO> getAllOptions() {
        return getOptionList(null);
    }

    @Override
    public Long createOption(SiteOptionSaveReqVO reqVO) {
        validateOption(reqVO);
        GalleryFilterOptionDO exists = optionMapper.selectByDimensionAndValue(reqVO.getDimension(),
                reqVO.getOptionValue());
        if (exists != null) {
            throw exception(OPTION_EXISTS);
        }
        GalleryFilterOptionDO option = BeanUtils.toBean(reqVO, GalleryFilterOptionDO.class);
        option.setId(null);
        option.setLabelZh(defaultLabel(reqVO.getLabelZh(), reqVO.getOptionValue()));
        option.setLabelEn(defaultLabel(reqVO.getLabelEn(), reqVO.getOptionValue()));
        optionMapper.insert(option);
        exportToSite();
        return option.getId();
    }

    @Override
    public void updateOption(SiteOptionSaveReqVO reqVO) {
        if (reqVO.getId() == null) {
            throw exception(OPTION_NOT_EXISTS);
        }
        GalleryFilterOptionDO exists = optionMapper.selectById(reqVO.getId());
        if (exists == null) {
            throw exception(OPTION_NOT_EXISTS);
        }
        validateOption(reqVO);
        GalleryFilterOptionDO same = optionMapper.selectByDimensionAndValue(reqVO.getDimension(),
                reqVO.getOptionValue());
        if (same != null && !same.getId().equals(reqVO.getId())) {
            throw exception(OPTION_EXISTS);
        }
        GalleryFilterOptionDO update = BeanUtils.toBean(reqVO, GalleryFilterOptionDO.class);
        update.setLabelZh(defaultLabel(reqVO.getLabelZh(), reqVO.getOptionValue()));
        update.setLabelEn(defaultLabel(reqVO.getLabelEn(), reqVO.getOptionValue()));
        optionMapper.updateById(update);
        exportToSite();
    }

    @Override
    public void deleteOption(Long id) {
        GalleryFilterOptionDO exists = optionMapper.selectById(id);
        if (exists == null) {
            throw exception(OPTION_NOT_EXISTS);
        }
        optionMapper.deletePhysicalById(id);
        exportToSite();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void sortOptions(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String dimension = null;
        for (int i = 0; i < ids.size(); i++) {
            GalleryFilterOptionDO exists = optionMapper.selectById(ids.get(i));
            if (exists == null) {
                throw exception(OPTION_NOT_EXISTS);
            }
            if (dimension == null) {
                dimension = exists.getDimension();
            } else if (!dimension.equals(exists.getDimension())) {
                throw exception(OPTION_INVALID);
            }
            GalleryFilterOptionDO update = new GalleryFilterOptionDO();
            update.setId(exists.getId());
            update.setSort((i + 1) * 10);
            optionMapper.updateById(update);
        }
        exportToSite();
    }

    private void validateOption(SiteOptionSaveReqVO reqVO) {
        if (!DIMENSIONS.contains(reqVO.getDimension())) {
            throw exception(OPTION_INVALID);
        }
        String value = reqVO.getOptionValue() == null ? "" : reqVO.getOptionValue().trim();
        boolean ok = "year".equals(reqVO.getDimension())
                ? YEAR_PATTERN.matcher(value).matches()
                : VALUE_PATTERN.matcher(value).matches();
        if (!ok) {
            throw exception(OPTION_INVALID);
        }
    }

    private String defaultLabel(String label, String value) {
        return label == null || label.isBlank() ? value : label.trim();
    }

    @Override
    public synchronized void exportToSite() {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (String dimension : List.of("venue", "year", "tier", "pattern")) {
            grouped.put(dimension, new ArrayList<>());
        }
        for (GalleryFilterOptionDO option : optionMapper.selectListOrdered()) {
            List<Map<String, Object>> list = grouped.get(option.getDimension());
            if (list == null) {
                continue;
            }
            if ("year".equals(option.getDimension())) {
                try {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("value", Integer.parseInt(option.getOptionValue()));
                    list.add(item);
                } catch (NumberFormatException ignored) {
                    // 非法年份在保存时已拦截，这里忽略脏数据
                }
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", option.getOptionValue());
            item.put("labelZh", defaultLabel(option.getLabelZh(), option.getOptionValue()));
            item.put("labelEn", defaultLabel(option.getLabelEn(), option.getOptionValue()));
            if ("venue".equals(option.getDimension())) {
                // 会议徽标 / 搜索用英文名，保持与旧数据一致
                item.put("label", defaultLabel(option.getLabelEn(), option.getOptionValue()));
            }
            list.add(item);
        }
        siteFileService.saveSiteConfig(siteFileService.buildConfig(grouped.get("venue"), grouped.get("year"),
                grouped.get("tier"), grouped.get("pattern")));
    }

    /**
     * 启动时同步一次，避免数据库与静态站配置漂移（表不存在等异常只记录日志）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            exportToSite();
        } catch (Exception e) {
            log.warn("[syncOnStartup] 导出前端配置失败：{}", e.getMessage());
        }
    }
}
