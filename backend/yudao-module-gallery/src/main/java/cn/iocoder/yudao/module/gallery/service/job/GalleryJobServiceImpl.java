package cn.iocoder.yudao.module.gallery.service.job;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobCreateReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobPageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryJobDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPaperDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryJobMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryPaperMapper;
import cn.iocoder.yudao.module.gallery.enums.GalleryConstants;
import cn.iocoder.yudao.module.gallery.service.paper.GalleryImportService;
import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.*;

@Service
@Validated
@Slf4j
public class GalleryJobServiceImpl implements GalleryJobService {

    @Resource
    private GalleryJobMapper galleryJobMapper;
    @Resource
    private GalleryPaperMapper paperMapper;
    @Resource
    private GallerySettingService settingService;
    @Resource
    private GalleryJobNotifier galleryJobNotifier;
    @Resource
    private GalleryImportService importService;
    @Resource
    private PipelineExecutor executor;
    @Resource
    private cn.iocoder.yudao.module.gallery.service.crop.GalleryCropService cropService;

    @PostConstruct
    public void init() {
        try {
            recoverInterruptedJobs();
        } catch (Exception e) {
            log.warn("[init] recover interrupted jobs failed", e);
        }
    }

    // ------------------------------------------------------------------ crawl
    @Override
    public Long createCrawlJob(JobCreateReqVO reqVO) {
        validateVenue(reqVO.getVenue(), reqVO.getYear());
        String dedupe = "CRAWL:" + reqVO.getVenue() + ":" + reqVO.getYear();
        guardConflict(dedupe);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("venue", reqVO.getVenue());
        params.put("year", reqVO.getYear());
        params.put("limit", reqVO.getLimit());
        GalleryJobDO job = GalleryJobDO.builder()
                .kind(GalleryConstants.JOB_CRAWL)
                .venue(reqVO.getVenue())
                .year(reqVO.getYear())
                .status(GalleryConstants.JOB_WAITING)
                .progress(0)
                .paramsJson(JsonUtils.toJsonString(params))
                .dedupeKey(dedupe)
                .build();
        galleryJobMapper.insert(job);
        notifyJob(job.getId());

        Path dir = jobDir(job.getId());
        List<String> cmd = baseCommand("collect",
                "--venue", reqVO.getVenue(),
                "--year", String.valueOf(reqVO.getYear()),
                "--out", dir.resolve("papers.jsonl").toString());
        if (reqVO.getLimit() != null && reqVO.getLimit() > 0) {
            cmd.add("--limit");
            cmd.add(String.valueOf(reqVO.getLimit()));
        }
        executor.submit(job, cmd, dir.resolve("job.log"), (finished, exitCode, result, error) -> {
            if (exitCode == 0) {
                int[] counts = importService.importCrawl(finished.getId(), dir.resolve("papers.jsonl").toString());
                Long jobId = finished.getId();
                int total = counts[0] + counts[1] + counts[2];
                // 采集任务的“完成”包含新导入与已存在（去重）的论文，“失败”为格式错误行
                updateCounters(jobId, total, counts[0] + counts[1], counts[2]);
                mergeResult(jobId, Map.of(
                        "imported", counts[0], "duplicated", counts[1], "invalid", counts[2],
                        "total", total, "done", counts[0] + counts[1], "failed", counts[2]));
                markAllFailed(jobId, counts[0] + counts[1], counts[2], "采集结果导入");
                notifyJob(jobId);
            }
        });
        return job.getId();
    }

    // ------------------------------------------------------------------ crop
    @Override
    public Long createCropJob(JobCreateReqVO reqVO) {
        List<GalleryPaperDO> papers;
        if (reqVO.getPaperIds() != null && !reqVO.getPaperIds().isEmpty()) {
            papers = paperMapper.selectListByIds(reqVO.getPaperIds());
        } else {
            if (reqVO.getVenue() == null || reqVO.getYear() == null) {
                throw exception(VENUE_INVALID);
            }
            validateVenue(reqVO.getVenue(), reqVO.getYear());
            int limit = reqVO.getCropLimit() == null || reqVO.getCropLimit() <= 0 ? 20 : reqVO.getCropLimit();
            papers = paperMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GalleryPaperDO>()
                    .eq(GalleryPaperDO::getVenue, reqVO.getVenue())
                    .eq(GalleryPaperDO::getYear, reqVO.getYear())
                    .in(GalleryPaperDO::getStatus, GalleryConstants.PAPER_NEW, GalleryConstants.PAPER_REJECTED)
                    .orderByAsc(GalleryPaperDO::getId)
                    .last("LIMIT " + limit));
        }
        if (papers.isEmpty()) {
            throw exception(NO_APPROVED_CROP);
        }

        String provider = "mineru".equalsIgnoreCase(reqVO.getParseProvider()) ? "mineru" : "local";
        if ("mineru".equals(provider) && settingService.get("mineru_api_token", "").isBlank()) {
            throw exception(MINERU_NOT_CONFIGURED);
        }
        GalleryJobDO job = GalleryJobDO.builder()
                .kind(GalleryConstants.JOB_CROP)
                .venue(reqVO.getVenue())
                .year(reqVO.getYear())
                .status(GalleryConstants.JOB_WAITING)
                .progress(0)
                .total(papers.size())
                .paramsJson(JsonUtils.toJsonString(Map.of(
                        "count", papers.size(),
                        "dpi", reqVO.getDpi() == null ? 0 : reqVO.getDpi(),
                        "parseProvider", provider,
                        "noFetch", Boolean.TRUE.equals(reqVO.getNoFetch()),
                        "includeCaption", Boolean.TRUE.equals(reqVO.getIncludeCaption()))))
                .dedupeKey("CROP:" + reqVO.getVenue() + ":" + reqVO.getYear() + ":" + System.currentTimeMillis())
                .build();
        galleryJobMapper.insert(job);
        notifyJob(job.getId());

        Path dir = jobDir(job.getId());
        List<Map<String, Object>> items = new ArrayList<>();
        for (GalleryPaperDO paper : papers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(paper.getId()));
            item.put("venue", paper.getVenue());
            item.put("year", paper.getYear());
            item.put("title", paper.getTitle());
            item.put("paper_url", paper.getPaperUrl());
            item.put("pdf_url", paper.getPdfUrl());
            if (paper.getPdfPath() != null && !paper.getPdfPath().isBlank()) {
                item.put("pdf_path", paper.getPdfPath());
            }
            items.add(item);
        }
        Path input = dir.resolve("items.json");
        try {
            Files.createDirectories(dir);
            Files.writeString(input, JsonUtils.toJsonString(Map.of("items", items)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw exception(PIPELINE_FAILED);
        }

        List<String> cmd = baseCommand("crop",
                "--input", input.toString(),
                "--out", dir.resolve("results.jsonl").toString(),
                "--provider", provider);
        if (Boolean.TRUE.equals(reqVO.getNoFetch())) {
            cmd.add("--no-fetch");
        }
        if (reqVO.getDpi() != null && reqVO.getDpi() > 0) {
            cmd.add("--dpi");
            cmd.add(String.valueOf(reqVO.getDpi()));
        }
        if (Boolean.TRUE.equals(reqVO.getIncludeCaption())) {
            cmd.add("--include-caption");
        }
        executor.submit(job, cmd, dir.resolve("job.log"), (finished, exitCode, result, error) -> {
            if (exitCode == 0) {
                int[] counts = importService.importCrop(finished.getId(), dir.resolve("results.jsonl").toString());
                Long jobId = finished.getId();
                updateCounters(jobId, counts[0] + counts[1], counts[0], counts[1]);
                mergeResult(jobId, Map.of("crops", counts[0], "failedCrops", counts[1],
                        "done", counts[0], "failed", counts[1]));
                // 管线在“全部条目失败”时仍返回 ok=true，这里按实际结果把任务标成失败
                markAllFailed(jobId, counts[0], counts[1],
                        "裁剪全部失败（请检查 PDF 是否已下载：批量裁剪时需开启“下载 PDF”，或检查解析配置）");
                notifyJob(jobId);
            }
        });
        return job.getId();
    }

    // ------------------------------------------------------------------ fetch
    @Override
    public Long createFetchJob(JobCreateReqVO reqVO) {
        List<GalleryPaperDO> papers = paperMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GalleryPaperDO>()
                        .eq(GalleryPaperDO::getVenue, reqVO.getVenue())
                        .eq(GalleryPaperDO::getYear, reqVO.getYear())
                        .in(GalleryPaperDO::getPdfStatus, GalleryConstants.PDF_NONE, GalleryConstants.PDF_FAILED)
                        .last("LIMIT " + (reqVO.getLimit() == null || reqVO.getLimit() <= 0 ? 50 : reqVO.getLimit())));
        if (papers.isEmpty()) {
            throw exception(NO_APPROVED_CROP);
        }
        GalleryJobDO job = GalleryJobDO.builder()
                .kind(GalleryConstants.JOB_FETCH)
                .venue(reqVO.getVenue())
                .year(reqVO.getYear())
                .status(GalleryConstants.JOB_WAITING)
                .total(papers.size())
                .dedupeKey("FETCH:" + reqVO.getVenue() + ":" + reqVO.getYear() + ":" + System.currentTimeMillis())
                .build();
        galleryJobMapper.insert(job);
        notifyJob(job.getId());
        Path dir = jobDir(job.getId());
        List<Map<String, Object>> items = new ArrayList<>();
        for (GalleryPaperDO paper : papers) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(paper.getId()));
            item.put("venue", paper.getVenue());
            item.put("year", paper.getYear());
            item.put("title", paper.getTitle());
            item.put("paper_url", paper.getPaperUrl());
            item.put("pdf_url", paper.getPdfUrl());
            items.add(item);
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("items.json"), JsonUtils.toJsonString(Map.of("items", items)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw exception(PIPELINE_FAILED);
        }
        List<String> cmd = baseCommand("fetch",
                "--input", dir.resolve("items.json").toString(),
                "--out", dir.resolve("fetch.jsonl").toString());
        executor.submit(job, cmd, dir.resolve("job.log"), (finished, exitCode, result, error) -> {
            if (exitCode == 0) {
                int[] counts = importService.importFetch(finished.getId(), dir.resolve("fetch.jsonl").toString());
                Long jobId = finished.getId();
                updateCounters(jobId, counts[0] + counts[1], counts[0], counts[1]);
                mergeResult(jobId, Map.of("fetched", counts[0], "failedFetch", counts[1],
                        "done", counts[0], "failed", counts[1]));
                markAllFailed(jobId, counts[0], counts[1], "PDF 下载");
                notifyJob(jobId);
            }
        });
        return job.getId();
    }

    // ------------------------------------------------------------------ query
    @Override
    public PageResult<GalleryJobDO> getJobPage(JobPageReqVO reqVO) {
        return galleryJobMapper.selectPage(reqVO);
    }

    @Override
    public GalleryJobDO getJob(Long id) {
        GalleryJobDO job = galleryJobMapper.selectById(id);
        if (job == null) {
            throw exception(JOB_NOT_EXISTS);
        }
        return job;
    }

    @Override
    public void cancelJob(Long id) {
        GalleryJobDO job = getJob(id);
        if (!Objects.equals(job.getStatus(), GalleryConstants.JOB_RUNNING)
                && !Objects.equals(job.getStatus(), GalleryConstants.JOB_WAITING)) {
            throw exception(JOB_NOT_RUNNING);
        }
        if (!executor.cancel(id)) {
            GalleryJobDO update = new GalleryJobDO();
            update.setId(id);
            update.setStatus(GalleryConstants.JOB_CANCELED);
            update.setFinishedTime(LocalDateTime.now());
            galleryJobMapper.updateById(update);
        }
    }

    @Override
    public void deleteJob(Long id) {
        GalleryJobDO job = getJob(id);
        if (Objects.equals(job.getStatus(), GalleryConstants.JOB_RUNNING)
                || Objects.equals(job.getStatus(), GalleryConstants.JOB_WAITING)) {
            throw exception(JOB_RUNNING_CANNOT_DELETE);
        }
        galleryJobMapper.deleteById(id);
        Path base = Path.of(settingService.workdir(), "workspace", "jobs").toAbsolutePath().normalize();
        Path dir = base.resolve(String.valueOf(id)).normalize();
        if (dir.startsWith(base) && Files.isDirectory(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("[deleteJob] 删除失败 {}: {}", path, e.getMessage());
                    }
                });
            } catch (IOException e) {
                log.warn("[deleteJob] 清理任务目录失败 {}: {}", dir, e.getMessage());
            }
        }
    }

    @Override
    public String getLog(Long id, int maxLines) {
        GalleryJobDO job = getJob(id);
        String path = job.getLogPath();
        if (path == null) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - Math.max(1, maxLines));
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public void recoverInterruptedJobs() {
        for (GalleryJobDO job : galleryJobMapper.selectRunning()) {
            GalleryJobDO update = new GalleryJobDO();
            update.setId(job.getId());
            update.setStatus(GalleryConstants.JOB_FAILED);
            update.setErrorMsg("服务重启，任务被中断；可重新创建任务继续（管线支持断点续跑）");
            update.setFinishedTime(LocalDateTime.now());
            galleryJobMapper.updateById(update);
        }
        for (GalleryJobDO job : galleryJobMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GalleryJobDO>()
                        .eq(GalleryJobDO::getStatus, GalleryConstants.JOB_WAITING))) {
            GalleryJobDO update = new GalleryJobDO();
            update.setId(job.getId());
            update.setStatus(GalleryConstants.JOB_CANCELED);
            update.setErrorMsg("服务重启，尚未开始的任务被取消");
            update.setFinishedTime(LocalDateTime.now());
            galleryJobMapper.updateById(update);
        }
    }

    // ------------------------------------------------------------------ helpers
    private void validateVenue(String venue, Integer year) {
        if (venue == null || year == null || !GalleryConstants.VENUES.contains(venue)) {
            throw exception(VENUE_INVALID);
        }
    }

    private void guardConflict(String dedupe) {
        GalleryJobDO last = galleryJobMapper.selectLastByDedupeKey(dedupe);
        if (last != null && (Objects.equals(last.getStatus(), GalleryConstants.JOB_RUNNING)
                || Objects.equals(last.getStatus(), GalleryConstants.JOB_WAITING))) {
            throw exception(JOB_CONFLICT);
        }
    }

    private Path jobDir(Long jobId) {
        return Path.of(settingService.workdir(), "workspace", "jobs", String.valueOf(jobId));
    }

    private List<String> baseCommand(String subCommand, String... args) {
        String python = settingService.python();
        String workdir = settingService.workdir();
        if (python == null || workdir == null) {
            throw exception(PIPELINE_NOT_CONFIGURED);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(python);
        cmd.add("-m");
        cmd.add("pipeline.cli");
        cmd.add(subCommand);
        cmd.addAll(List.of(args));
        return cmd;
    }

    private void notifyJob(Long jobId) {
        try {
            galleryJobNotifier.notifyJobChanged(galleryJobMapper.selectById(jobId));
        } catch (Exception ignored) {
            // SSE 推送失败不影响任务
        }
    }

    private void updateCounters(Long jobId, Integer total, Integer done, Integer failed) {
        GalleryJobDO update = new GalleryJobDO();
        update.setId(jobId);
        update.setTotal(total);
        update.setDone(done);
        update.setFailed(failed);
        galleryJobMapper.updateById(update);
    }

    /**
     * 管线的 @@RESULT.ok 只代表“命令正常结束”，全部条目失败时也会是 true。
     * 这里按导入结果兜底：成功 0 条且失败大于 0 时把任务标记为失败，避免日志与状态不一致。
     */
    private void markAllFailed(Long jobId, int ok, int failed, String label) {
        if (ok > 0 || failed <= 0) {
            return;
        }
        GalleryJobDO update = new GalleryJobDO();
        update.setId(jobId);
        update.setStatus(GalleryConstants.JOB_FAILED);
        update.setErrorMsg(label + "：全部 " + failed + " 项失败，请查看任务日志");
        galleryJobMapper.updateById(update);
    }

    private void mergeResult(Long jobId, Map<String, Object> extra) {
        GalleryJobDO job = galleryJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (job.getResultJson() != null) {
            Map<String, Object> old = JsonUtils.parseObjectQuietly(job.getResultJson(), Map.class);
            if (old != null) {
                merged.putAll(old);
            }
        }
        merged.putAll(extra);
        GalleryJobDO update = new GalleryJobDO();
        update.setId(jobId);
        update.setResultJson(JsonUtils.toJsonString(merged));
        galleryJobMapper.updateById(update);
    }
}
