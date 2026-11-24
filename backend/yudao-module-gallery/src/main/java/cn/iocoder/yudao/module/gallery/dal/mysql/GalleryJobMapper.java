package cn.iocoder.yudao.module.gallery.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobPageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryJobDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GalleryJobMapper extends BaseMapperX<GalleryJobDO> {

    default PageResult<GalleryJobDO> selectPage(JobPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<GalleryJobDO>()
                .eqIfPresent(GalleryJobDO::getKind, reqVO.getKind())
                .eqIfPresent(GalleryJobDO::getStatus, reqVO.getStatus())
                .eqIfPresent(GalleryJobDO::getVenue, reqVO.getVenue())
                .eqIfPresent(GalleryJobDO::getYear, reqVO.getYear())
                .in(Boolean.TRUE.equals(reqVO.getPaperOnly()), GalleryJobDO::getKind, List.of("CRAWL", "CROP"))
                .orderByDesc(GalleryJobDO::getId));
    }

    default GalleryJobDO selectLastByDedupeKey(String dedupeKey) {
        List<GalleryJobDO> list = selectList(new LambdaQueryWrapperX<GalleryJobDO>()
                .eq(GalleryJobDO::getDedupeKey, dedupeKey)
                .orderByDesc(GalleryJobDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    default List<GalleryJobDO> selectRunning() {
        return selectList(new LambdaQueryWrapperX<GalleryJobDO>().eq(GalleryJobDO::getStatus, 1));
    }

    default List<GalleryJobDO> selectRecent(int limit) {
        return selectList(new LambdaQueryWrapperX<GalleryJobDO>()
                .orderByDesc(GalleryJobDO::getId).last("LIMIT " + Math.max(1, limit)));
    }

    default long countByStatus(Integer status) {
        return selectCount(GalleryJobDO::getStatus, status);
    }
}
