package cn.iocoder.yudao.module.gallery.service.job;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobCreateReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobPageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryJobDO;

public interface GalleryJobService {

    Long createCrawlJob(JobCreateReqVO reqVO);

    Long createCropJob(JobCreateReqVO reqVO);

    Long createFetchJob(JobCreateReqVO reqVO);

    PageResult<GalleryJobDO> getJobPage(JobPageReqVO reqVO);

    GalleryJobDO getJob(Long id);

    void cancelJob(Long id);

    String getLog(Long id, int maxLines);

    /** 删除已完成的任务记录及其工作目录 */
    void deleteJob(Long id);

    /** 启动时把上次异常中断的任务标记出来，避免“永远运行中” */
    void recoverInterruptedJobs();
}
