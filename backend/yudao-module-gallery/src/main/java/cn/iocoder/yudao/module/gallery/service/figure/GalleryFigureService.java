package cn.iocoder.yudao.module.gallery.service.figure;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigurePageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigureRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.figure.vo.FigureUpdateReqVO;
import org.springframework.web.multipart.MultipartFile;

public interface GalleryFigureService {

    PageResult<FigureRespVO> getFigurePage(FigurePageReqVO reqVO);

    FigureRespVO getFigure(Long id);

    void updateFigure(FigureUpdateReqVO reqVO);

    /** 用上传的图片替换原图，返回新的宽高。 */
    int[] replaceImage(Long id, MultipartFile file);

    void deleteFigure(Long id);
}
