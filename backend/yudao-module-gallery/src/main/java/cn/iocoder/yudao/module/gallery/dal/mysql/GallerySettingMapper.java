package cn.iocoder.yudao.module.gallery.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GallerySettingDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GallerySettingMapper extends BaseMapperX<GallerySettingDO> {

    default GallerySettingDO selectByKey(String key) {
        return selectOne(GallerySettingDO::getConfigKey, key);
    }
}
