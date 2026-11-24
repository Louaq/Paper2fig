package cn.iocoder.yudao.module.gallery.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewPageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryCropDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GalleryCropMapper extends BaseMapperX<GalleryCropDO> {

    /** 清空论文库时彻底删除全部裁剪记录（含逻辑删除行）。 */
    @Delete("DELETE FROM gallery_crop")
    int deleteAllRows();

    default PageResult<GalleryCropDO> selectPage(ReviewPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<GalleryCropDO>()
                .eqIfPresent(GalleryCropDO::getStatus, reqVO.getStatus())
                .eqIfPresent(GalleryCropDO::getPaperId, reqVO.getPaperId())
                .eqIfPresent(GalleryCropDO::getJobId, reqVO.getJobId())
                .ge(reqVO.getMinConfidence() != null, GalleryCropDO::getConfidence, reqVO.getMinConfidence())
                .le(reqVO.getMaxConfidence() != null, GalleryCropDO::getConfidence, reqVO.getMaxConfidence())
                .orderByAsc(GalleryCropDO::getStatus)
                .orderByDesc(GalleryCropDO::getConfidence));
    }

    default List<GalleryCropDO> selectByStatus(Integer status, int limit) {
        return selectList(new LambdaQueryWrapperX<GalleryCropDO>()
                .eq(GalleryCropDO::getStatus, status)
                .orderByDesc(GalleryCropDO::getConfidence)
                .last("LIMIT " + Math.max(1, limit)));
    }

    default long countByStatus(Integer status) {
        return selectCount(GalleryCropDO::getStatus, status);
    }

    default GalleryCropDO selectByPaperId(Long paperId) {
        return selectOne(new LambdaQueryWrapperX<GalleryCropDO>()
                .eq(GalleryCropDO::getPaperId, paperId)
                .orderByDesc(GalleryCropDO::getId));
    }

    default GalleryCropDO selectByPhash(String phash) {
        return selectOne(GalleryCropDO::getPhash, phash);
    }

    @org.apache.ibatis.annotations.Select("SELECT * FROM gallery_crop WHERE id = #{id}")
    GalleryCropDO selectByIdIncludeDeleted(@org.apache.ibatis.annotations.Param("id") Long id);

    default List<GalleryCropDO> selectListByJobId(Long jobId) {
        return selectList(new LambdaQueryWrapperX<GalleryCropDO>()
                .eq(GalleryCropDO::getJobId, jobId));
    }

    /** 发布后清理工作区裁剪图：只清空路径，保留质检与审核元数据。 */
    @Update("UPDATE gallery_crop SET image_path = NULL, update_time = NOW() WHERE id = #{id}")
    int clearImagePath(@Param("id") Long id);
}
