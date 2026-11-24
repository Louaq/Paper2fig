package cn.iocoder.yudao.module.gallery.service.job;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.sse.core.sender.SseMessageSender;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobRespVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryJobDO;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 画廊任务状态推送：任务进度/结束通过 SSE 异步通知前端，避免页面定时轮询。
 *
 * yudao.sse.enable=false 时 SseMessageSender 不存在，这里静默降级为“前端手动刷新”。
 */
@Slf4j
@Component
public class GalleryJobNotifier {

    public static final String EVENT_TYPE = "gallery-job-update";

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired(required = false)
    private SseMessageSender sseMessageSender;

    public void notifyJobChanged(GalleryJobDO job) {
        if (job == null) {
            return;
        }
        if (sseMessageSender == null) {
            return;
        }
        try {
            JobRespVO message = BeanUtils.toBean(job, JobRespVO.class);
            // 广播给所有管理员：画廊后台通常只有少数管理员，连接数很少
            sseMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(), EVENT_TYPE, message);
        } catch (Exception e) {
            // 推送失败不能影响管线执行
            log.warn("[notifyJobChanged][job {}] 推送失败：{}", job.getId(), e.getMessage());
        }
    }
}
