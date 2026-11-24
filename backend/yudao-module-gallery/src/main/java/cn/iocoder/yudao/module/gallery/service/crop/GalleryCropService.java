package cn.iocoder.yudao.module.gallery.service.crop;

import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryCropDO;

import java.util.List;

public interface GalleryCropService {

    GalleryCropDO validateCropExists(Long id);

    void approve(Long id, ReviewReqVO reqVO, String reviewer);

    void reject(Long id, String comment, String reviewer);

    void batchApprove(List<Long> ids, String pattern, String reviewer);

    /** 重新排队：把裁剪记录置为拒绝，论文回到待处理，等待下一次裁剪 */
    void requeue(Long id, String reviewer);

    /** 手动删除裁剪图：删除图片文件与裁剪记录，未发布的论文回到待处理 */
    void deleteCrop(Long id, String reviewer);
}
