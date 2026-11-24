package cn.iocoder.yudao.module.gallery.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperPageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPaperDO;
import cn.iocoder.yudao.module.gallery.enums.GalleryConstants;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface GalleryPaperMapper extends BaseMapperX<GalleryPaperDO> {

    default PageResult<GalleryPaperDO> selectPage(PaperPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<GalleryPaperDO>()
                .eqIfPresent(GalleryPaperDO::getVenue, reqVO.getVenue())
                .eqIfPresent(GalleryPaperDO::getYear, reqVO.getYear())
                .eqIfPresent(GalleryPaperDO::getStatus, reqVO.getStatus())
                .eqIfPresent(GalleryPaperDO::getPdfStatus, reqVO.getPdfStatus())
                .likeIfPresent(GalleryPaperDO::getPattern, reqVO.getPattern())
                .likeIfPresent(GalleryPaperDO::getTier, reqVO.getTier())
                .and(reqVO.getKeyword() != null && !reqVO.getKeyword().isEmpty(),
                        w -> w.like(GalleryPaperDO::getTitle, reqVO.getKeyword())
                                .or().like(GalleryPaperDO::getAuthors, reqVO.getKeyword())
                                .or().like(GalleryPaperDO::getSourceId, reqVO.getKeyword())
                                .or().like(GalleryPaperDO::getDoi, reqVO.getKeyword()))
                .betweenIfPresent(GalleryPaperDO::getCreateTime, reqVO.getCreateTime())
                .ne(reqVO.getStatus() == null && !Boolean.TRUE.equals(reqVO.getIncludePublished()),
                        GalleryPaperDO::getStatus, GalleryConstants.PAPER_PUBLISHED)
                .orderByDesc(GalleryPaperDO::getId));
    }

    default GalleryPaperDO selectBySource(String source, String sourceId) {
        return selectOne(new LambdaQueryWrapperX<GalleryPaperDO>()
                .eq(GalleryPaperDO::getSource, source)
                .eq(GalleryPaperDO::getSourceId, sourceId));
    }

    default GalleryPaperDO selectByTitleNorm(String venue, String titleNorm) {
        return selectOne(new LambdaQueryWrapperX<GalleryPaperDO>()
                .eq(GalleryPaperDO::getVenue, venue)
                .eq(GalleryPaperDO::getTitleNorm, titleNorm));
    }

    /**
     * 含逻辑删除行（deleted=1）的查询。唯一键 uk_source 不区分 deleted，
     * 已删除的论文仍会阻塞同一条目的重新采集，因此导入时需要能看到它们。
     */
    @Select("SELECT * FROM gallery_paper WHERE source = #{source} AND source_id = #{sourceId} LIMIT 1")
    GalleryPaperDO selectBySourceIgnoreDeleted(@Param("source") String source, @Param("sourceId") String sourceId);

    @Select("SELECT * FROM gallery_paper WHERE venue = #{venue} AND title_norm = #{titleNorm} LIMIT 1")
    GalleryPaperDO selectByTitleNormIgnoreDeleted(@Param("venue") String venue, @Param("titleNorm") String titleNorm);

    /** 恢复一条被逻辑删除的论文，并刷新采集到的元数据。 */
    @Update("UPDATE gallery_paper SET deleted = 0, venue = #{p.venue}, year = #{p.year}, source = #{p.source}, "
            + "source_id = #{p.sourceId}, dedupe_key = #{p.dedupeKey}, title = #{p.title}, title_norm = #{p.titleNorm}, "
            + "authors = #{p.authors}, abstract_text = #{p.abstractText}, paper_url = #{p.paperUrl}, pdf_url = #{p.pdfUrl}, "
            + "doi = #{p.doi}, arxiv_id = #{p.arxivId}, decision = #{p.decision}, track = #{p.track}, "
            + "status = 0, pdf_status = 0, pdf_path = NULL, pdf_pages = NULL, pdf_size = NULL, pdf_error = NULL, "
            + "crop_id = NULL, update_time = NOW() WHERE id = #{p.id}")
    int revive(@Param("p") GalleryPaperDO paper);

    default List<GalleryPaperDO> selectByDedupeKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<GalleryPaperDO>().in(GalleryPaperDO::getDedupeKey, keys));
    }

    default List<GalleryPaperDO> selectListByIds(Collection<Long> ids) {
        return selectList(GalleryPaperDO::getId, ids);
    }

    default long countByStatus(Integer status) {
        return selectCount(GalleryPaperDO::getStatus, status);
    }

    /** 手动清理用：已发布（status=4）或已逻辑删除的论文，忽略 deleted 过滤。 */
    @Select("SELECT * FROM gallery_paper WHERE deleted = 1 OR status = 4")
    List<GalleryPaperDO> selectPublishedOrDeleted();

    /** 发布后清理原始 PDF：清空路径并恢复为“未缓存”。 */
    @Update("UPDATE gallery_paper SET pdf_path = NULL, pdf_status = 0, update_time = NOW() WHERE id = #{id}")
    int clearPdfArtifact(@Param("id") Long id);

    /** 清空论文库时彻底删除全部论文（含逻辑删除行）。 */
    @Delete("DELETE FROM gallery_paper")
    int deleteAllRows();

    /** 删除裁剪图后：清空论文的当前裁剪引用，并恢复到指定状态。 */
    @Update("UPDATE gallery_paper SET crop_id = NULL, status = #{status}, update_time = NOW() WHERE id = #{id}")
    int resetCrop(@Param("id") Long id, @Param("status") Integer status);

    default long countByVenueAndYear(String venue, Integer year) {
        return selectCount(new LambdaQueryWrapperX<GalleryPaperDO>()
                .eq(GalleryPaperDO::getVenue, venue)
                .eq(GalleryPaperDO::getYear, year));
    }

    @Select("SELECT venue, COUNT(*) AS cnt FROM gallery_paper WHERE deleted = 0 GROUP BY venue")
    List<Map<String, Object>> countGroupByVenue();

    @Select("SELECT status, COUNT(*) AS cnt FROM gallery_paper WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countGroupByStatus();
}
