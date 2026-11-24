package cn.iocoder.yudao.module.gallery.job;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobCreateReqVO;
import cn.iocoder.yudao.module.gallery.service.job.GalleryJobService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 定时发现新论文。
 *
 * 在「基础设施 -> 定时任务」里配置，处理器名字 {@code galleryCrawlJob}，参数支持三种写法：
 *   cvpr,2026,100           会议,年份,上限
 *   {"venue":"cvpr","year":2026,"limit":100}
 *   cvpr                    只给会议，年份取今年、上限 100
 *
 * 另外每个会议还有一个专用处理器 {@code galleryCrawlJob<Venue>}（见
 * {@link GalleryCrawlJobConfig}），参数固定为该会议，只需写 {@code 2026,100}。
 */
@Component
@Slf4j
public class GalleryCrawlJob implements JobHandler {

    @Resource
    private GalleryJobService galleryJobService;

    @Override
    public String execute(String param) {
        return executeFor(galleryJobService, null, param);
    }

    /**
     * @param fixedVenue 非空时强制使用该会议（每会议独立定时任务）
     */
    public static String executeFor(GalleryJobService service, String fixedVenue, String param) {
        JobCreateReqVO req = parse(param, fixedVenue);
        Long jobId = service.createCrawlJob(req);
        log.info("[executeFor][创建采集任务 {}：{} {} 上限 {}]", jobId, req.getVenue(), req.getYear(), req.getLimit());
        return "采集任务已创建：#" + jobId + "（" + req.getVenue() + " " + req.getYear() + "）";
    }

    @SuppressWarnings("unchecked")
    private static JobCreateReqVO parse(String param, String fixedVenue) {
        JobCreateReqVO req = new JobCreateReqVO();
        String text = param == null ? "" : param.trim();
        if (text.startsWith("{")) {
            Map<String, Object> map = JsonUtils.parseObjectQuietly(text, Map.class);
            if (map != null) {
                req.setVenue(fixedVenue != null ? fixedVenue
                        : String.valueOf(map.getOrDefault("venue", "cvpr")));
                req.setYear(((Number) map.getOrDefault("year", LocalDate.now().getYear())).intValue());
                req.setLimit(((Number) map.getOrDefault("limit", 100)).intValue());
                return req;
            }
        }
        String[] parts = text.split("[,，\\s]+");
        if (fixedVenue != null) {
            java.util.List<String> numbers = new java.util.ArrayList<>();
            for (String part : parts) {
                if (part != null && part.matches("\\d+")) {
                    numbers.add(part);
                }
            }
            req.setVenue(fixedVenue);
            req.setYear(numbers.size() > 0 ? Integer.parseInt(numbers.get(0)) : LocalDate.now().getYear());
            req.setLimit(numbers.size() > 1 ? Integer.parseInt(numbers.get(1)) : 100);
            return req;
        }
        req.setVenue(parts.length > 0 && !parts[0].isBlank() ? parts[0].toLowerCase() : "cvpr");
        req.setYear(parts.length > 1 ? Integer.parseInt(parts[1]) : LocalDate.now().getYear());
        req.setLimit(parts.length > 2 ? Integer.parseInt(parts[2]) : 100);
        return req;
    }
}
