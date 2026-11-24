package cn.iocoder.yudao.module.gallery.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.gallery.controller.admin.publish.vo.PublishPageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPublishDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface GalleryPublishMapper extends BaseMapperX<GalleryPublishDO> {

    default PageResult<GalleryPublishDO> selectPage(PublishPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<GalleryPublishDO>()
                .eqIfPresent(GalleryPublishDO::getStatus, reqVO.getStatus())
                .orderByDesc(GalleryPublishDO::getId));
    }

    default List<GalleryPublishDO> selectLatest(int limit) {
        return selectList(new LambdaQueryWrapperX<GalleryPublishDO>()
                .orderByDesc(GalleryPublishDO::getId).last("LIMIT " + Math.max(1, limit)));
    }

    default GalleryPublishDO selectLastSuccess() {
        List<GalleryPublishDO> list = selectList(new LambdaQueryWrapperX<GalleryPublishDO>()
                .eq(GalleryPublishDO::getStatus, 1)
                .orderByDesc(GalleryPublishDO::getId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}
