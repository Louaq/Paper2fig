package cn.iocoder.yudao.module.gallery.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigurePageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryFigureDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GalleryFigureMapper extends BaseMapperX<GalleryFigureDO> {

    default PageResult<GalleryFigureDO> selectPage(FigurePageReqVO reqVO) {
        LambdaQueryWrapperX<GalleryFigureDO> wrapper = new LambdaQueryWrapperX<GalleryFigureDO>()
                .eqIfPresent(GalleryFigureDO::getVenue, reqVO.getVenue())
                .eqIfPresent(GalleryFigureDO::getYear, reqVO.getYear())
                .eqIfPresent(GalleryFigureDO::getPattern, reqVO.getPattern());
        // “最佳论文”对应 award 字段，其余等级对应 tier
        if ("best".equals(reqVO.getTier())) {
            wrapper.isNotNull(GalleryFigureDO::getAward);
        } else {
            wrapper.eqIfPresent(GalleryFigureDO::getTier, reqVO.getTier());
        }
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(GalleryFigureDO::getTitle, reqVO.getKeyword())
                    .or().like(GalleryFigureDO::getAuthors, reqVO.getKeyword())
                    .or().like(GalleryFigureDO::getFigureId, reqVO.getKeyword()));
        }
        // 与静态站默认排序一致：会议（按前端配置的 sort）→ 年份升序 → 图片 ID
        wrapper.last("ORDER BY COALESCE((SELECT o.sort FROM gallery_filter_option o"
                + " WHERE o.dimension = 'venue' AND o.option_value = gallery_figure.venue AND o.deleted = 0), 9999) ASC,"
                + " gallery_figure.year ASC, gallery_figure.figure_id ASC");
        return selectPage(reqVO, wrapper);
    }

    default GalleryFigureDO selectByFigureId(String figureId) {
        return selectOne(GalleryFigureDO::getFigureId, figureId);
    }

    /** 按写入顺序读取全部已发布图片（用于生成静态站文件）。 */
    default List<GalleryFigureDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapperX<GalleryFigureDO>()
                .orderByAsc(GalleryFigureDO::getId));
    }

    /** 替换图片后更新尺寸并标记人工编辑。 */
    default int updateImageAndMarkManual(Long id, Integer width, Integer height) {
        return update(null, new LambdaUpdateWrapper<GalleryFigureDO>()
                .eq(GalleryFigureDO::getId, id)
                .set(GalleryFigureDO::getWidth, width)
                .set(GalleryFigureDO::getHeight, height)
                .set(GalleryFigureDO::getOrigin, "MANUAL")
                .set(GalleryFigureDO::getManualEditedTime, LocalDateTime.now()));
    }

    /** 流水线强制覆盖后清除人工编辑标记。 */
    default int clearManualEdited(Long id) {
        return update(null, new LambdaUpdateWrapper<GalleryFigureDO>()
                .eq(GalleryFigureDO::getId, id)
                .set(GalleryFigureDO::getManualEditedTime, null)
                .set(GalleryFigureDO::getOrigin, "PIPELINE"));
    }

    /**
     * 全字段更新：把 null 也写回数据库，用于清空等级/奖项/备注等可选属性。
     */
    default int updateAllFields(GalleryFigureDO figure) {
        LambdaUpdateWrapper<GalleryFigureDO> wrapper = new LambdaUpdateWrapper<GalleryFigureDO>()
                .eq(GalleryFigureDO::getId, figure.getId())
                .set(figure.getPaperId() != null, GalleryFigureDO::getPaperId, figure.getPaperId())
                .set(figure.getOrigin() != null, GalleryFigureDO::getOrigin, figure.getOrigin())
                .set(figure.getManualEditedTime() != null, GalleryFigureDO::getManualEditedTime, figure.getManualEditedTime())
                .set(GalleryFigureDO::getVenue, figure.getVenue())
                .set(GalleryFigureDO::getYear, figure.getYear())
                .set(GalleryFigureDO::getTitle, figure.getTitle())
                .set(GalleryFigureDO::getAuthors, figure.getAuthors())
                .set(GalleryFigureDO::getPattern, figure.getPattern())
                .set(GalleryFigureDO::getTier, figure.getTier())
                .set(GalleryFigureDO::getAward, figure.getAward())
                .set(GalleryFigureDO::getPaper, figure.getPaper())
                .set(GalleryFigureDO::getPdfSource, figure.getPdfSource())
                .set(GalleryFigureDO::getScore, figure.getScore())
                .set(GalleryFigureDO::getRemark, figure.getRemark());
        return update(null, wrapper);
    }

    @Delete("DELETE FROM gallery_figure WHERE id = #{id}")
    int deletePhysicalById(@Param("id") Long id);
}
