package cn.iocoder.yudao.module.gallery.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryFilterOptionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GalleryFilterOptionMapper extends BaseMapperX<GalleryFilterOptionDO> {

    default List<GalleryFilterOptionDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapperX<GalleryFilterOptionDO>()
                .orderByAsc(GalleryFilterOptionDO::getDimension)
                .orderByAsc(GalleryFilterOptionDO::getSort)
                .orderByAsc(GalleryFilterOptionDO::getId));
    }

    default List<GalleryFilterOptionDO> selectListByDimension(String dimension) {
        return selectList(new LambdaQueryWrapperX<GalleryFilterOptionDO>()
                .eq(GalleryFilterOptionDO::getDimension, dimension)
                .orderByAsc(GalleryFilterOptionDO::getSort)
                .orderByAsc(GalleryFilterOptionDO::getId));
    }

    default GalleryFilterOptionDO selectByDimensionAndValue(String dimension, String optionValue) {
        return selectOne(new LambdaQueryWrapperX<GalleryFilterOptionDO>()
                .eq(GalleryFilterOptionDO::getDimension, dimension)
                .eq(GalleryFilterOptionDO::getOptionValue, optionValue));
    }

    @Delete("DELETE FROM gallery_filter_option WHERE id = #{id}")
    int deletePhysicalById(@Param("id") Long id);
}
