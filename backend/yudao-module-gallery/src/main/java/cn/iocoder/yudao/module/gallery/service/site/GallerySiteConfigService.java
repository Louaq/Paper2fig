package cn.iocoder.yudao.module.gallery.service.site;

import cn.iocoder.yudao.module.gallery.controller.admin.site.vo.SiteOptionRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.site.vo.SiteOptionSaveReqVO;

import java.util.List;

public interface GallerySiteConfigService {

    List<SiteOptionRespVO> getOptionList(String dimension);

    List<SiteOptionRespVO> getAllOptions();

    Long createOption(SiteOptionSaveReqVO reqVO);

    void updateOption(SiteOptionSaveReqVO reqVO);

    void deleteOption(Long id);

    /** 按拖拽后的顺序重排同一维度的筛选项 */
    void sortOptions(List<Long> ids);

    /** 把当前配置导出到静态站（assets/site-config.js + data/site-config.json）。 */
    void exportToSite();
}
