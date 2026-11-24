package cn.iocoder.yudao.module.gallery.service.paper;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperPageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperUpdateReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewRespVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPaperDO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface GalleryPaperService {

    PageResult<PaperRespVO> getPaperPage(PaperPageReqVO reqVO);

    PaperRespVO getPaper(Long id);

    GalleryPaperDO validatePaperExists(Long id);

    void updatePaper(PaperUpdateReqVO reqVO);

    /** 手动上传论文 PDF，创建论文记录并把文件放进管线 PDF 缓存目录 */
    Long uploadPdf(MultipartFile file, String venue, Integer year, String title, String authors, String doi) throws IOException;

    /** 清空论文库：删除全部论文记录、PDF 文件与已裁剪图片（已发布到静态站的图片不受影响） */
    java.util.Map<String, Object> clearAllPapers();

    void deletePaper(Long id);

    PageResult<ReviewRespVO> getReviewPage(cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewPageReqVO reqVO);

    java.util.Map<String, Object> getDashboard();
}
