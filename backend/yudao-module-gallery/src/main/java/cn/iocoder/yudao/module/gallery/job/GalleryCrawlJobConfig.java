package cn.iocoder.yudao.module.gallery.job;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.module.gallery.service.job.GalleryJobService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 每个会议一个独立的定时任务处理器。
 *
 * yudao 的 infra_job 以处理器名字作为唯一键，所以「每会议一个任务」必须有独立的
 * 处理器 Bean；参数格式为 {@code 年份,上限}，会议由 Bean 固定。
 */
@Configuration
public class GalleryCrawlJobConfig {

    private static JobHandler handler(GalleryJobService service, String venue) {
        return param -> GalleryCrawlJob.executeFor(service, venue, param);
    }

    @Bean("galleryCrawlJobIclr")
    public JobHandler galleryCrawlJobIclr(GalleryJobService service) {
        return handler(service, "iclr");
    }

    @Bean("galleryCrawlJobIcml")
    public JobHandler galleryCrawlJobIcml(GalleryJobService service) {
        return handler(service, "icml");
    }

    @Bean("galleryCrawlJobNeurips")
    public JobHandler galleryCrawlJobNeurips(GalleryJobService service) {
        return handler(service, "neurips");
    }

    @Bean("galleryCrawlJobCvpr")
    public JobHandler galleryCrawlJobCvpr(GalleryJobService service) {
        return handler(service, "cvpr");
    }

    @Bean("galleryCrawlJobAcl")
    public JobHandler galleryCrawlJobAcl(GalleryJobService service) {
        return handler(service, "acl");
    }

    @Bean("galleryCrawlJobAaai")
    public JobHandler galleryCrawlJobAaai(GalleryJobService service) {
        return handler(service, "aaai");
    }

    @Bean("galleryCrawlJobEccv")
    public JobHandler galleryCrawlJobEccv(GalleryJobService service) {
        return handler(service, "eccv");
    }

    @Bean("galleryCrawlJobMiccai")
    public JobHandler galleryCrawlJobMiccai(GalleryJobService service) {
        return handler(service, "miccai");
    }

    @Bean("galleryCrawlJobIjcai")
    public JobHandler galleryCrawlJobIjcai(GalleryJobService service) {
        return handler(service, "ijcai");
    }

    @Bean("galleryCrawlJobIcme")
    public JobHandler galleryCrawlJobIcme(GalleryJobService service) {
        return handler(service, "icme");
    }

    @Bean("galleryCrawlJobMm")
    public JobHandler galleryCrawlJobMm(GalleryJobService service) {
        return handler(service, "mm");
    }
}
