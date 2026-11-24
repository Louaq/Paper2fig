package cn.iocoder.yudao.module.gallery.service.paper;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperPageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperRespVO;
import cn.iocoder.yudao.module.gallery.controller.admin.paper.vo.PaperUpdateReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewPageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.review.vo.ReviewRespVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryCropDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryJobDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPaperDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPublishDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryCropMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryJobMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryPaperMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryPublishMapper;
import cn.iocoder.yudao.module.gallery.enums.GalleryConstants;
import cn.iocoder.yudao.module.gallery.framework.Thumbnails;
import cn.iocoder.yudao.module.gallery.service.artifacts.GalleryArtifactCleanupService;
import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.PAPER_NOT_EXISTS;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.PAPER_PDF_INVALID;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.PAPER_PDF_SAVE_FAILED;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.VENUE_INVALID;

@Service
@Validated
@Slf4j
public class GalleryPaperServiceImpl implements GalleryPaperService {

    @Resource
    private GalleryPaperMapper paperMapper;
    @Resource
    private GalleryCropMapper cropMapper;
    @Resource
    private GalleryJobMapper galleryJobMapper;
    @Resource
    private GalleryPublishMapper publishMapper;
    @Resource
    private GallerySettingService settingService;
    @Resource
    private GalleryArtifactCleanupService artifactCleanupService;

    @Override
    public PageResult<PaperRespVO> getPaperPage(PaperPageReqVO reqVO) {
        PageResult<GalleryPaperDO> page = paperMapper.selectPage(reqVO);
        List<PaperRespVO> list = new ArrayList<>(page.getList().size());
        for (GalleryPaperDO paper : page.getList()) {
            PaperRespVO vo = BeanUtils.toBean(paper, PaperRespVO.class);
            vo.setAuthors(parseAuthors(paper.getAuthors()));
            if (paper.getCropId() != null) {
                GalleryCropDO crop = cropMapper.selectById(paper.getCropId());
                if (crop != null) {
                    vo.setThumb(Thumbnails.dataUri(crop.getImagePath(), 360));
                    vo.setConfidence(crop.getConfidence());
                    vo.setFlags(crop.getFlagsJson());
                    vo.setCropWidth(crop.getWidth());
                    vo.setCropHeight(crop.getHeight());
                }
            }
            list.add(vo);
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public PaperRespVO getPaper(Long id) {
        GalleryPaperDO paper = validatePaperExists(id);
        PaperRespVO vo = BeanUtils.toBean(paper, PaperRespVO.class);
        vo.setAuthors(parseAuthors(paper.getAuthors()));
        if (paper.getCropId() != null) {
            GalleryCropDO crop = cropMapper.selectById(paper.getCropId());
            if (crop != null) {
                vo.setThumb(Thumbnails.dataUri(crop.getImagePath(), 1000));
                vo.setConfidence(crop.getConfidence());
                vo.setFlags(crop.getFlagsJson());
                vo.setCropWidth(crop.getWidth());
                vo.setCropHeight(crop.getHeight());
            }
        }
        return vo;
    }

    @Override
    public GalleryPaperDO validatePaperExists(Long id) {
        GalleryPaperDO paper = paperMapper.selectById(id);
        if (paper == null) {
            throw exception(PAPER_NOT_EXISTS);
        }
        return paper;
    }

    @Override
    public Long uploadPdf(MultipartFile file, String venue, Integer year, String title,
                          String authors, String doi) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 100L * 1024 * 1024) {
            throw exception(PAPER_PDF_INVALID);
        }
        if (venue == null || year == null || year < 1990 || year > 2100
                || !GalleryConstants.VENUES.contains(venue)) {
            throw exception(VENUE_INVALID);
        }
        // 只接受真正的 PDF（魔数 %PDF-），避免把其它文件塞进管线
        byte[] head = new byte[5];
        try (InputStream in = file.getInputStream()) {
            if (in.read(head) != 5
                    || !"%PDF-".equals(new String(head, StandardCharsets.ISO_8859_1))) {
                throw exception(PAPER_PDF_INVALID);
            }
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String fallbackTitle = filename.replaceAll("(?i)\\.pdf$", "").trim();
        String paperTitle = title == null || title.isBlank() ? fallbackTitle : title.trim();
        if (paperTitle.isBlank()) {
            throw exception(PAPER_PDF_INVALID);
        }
        paperTitle = truncate(paperTitle, 500);
        String titleNorm = GalleryImportServiceImpl.normalizeTitle(paperTitle);
        String sourceId = UUID.randomUUID().toString();

        GalleryPaperDO paper = GalleryPaperDO.builder()
                .venue(venue)
                .year(year)
                .source("upload")
                .sourceId(sourceId)
                .dedupeKey(truncate("upload:" + sourceId + "|title:" + titleNorm, 250))
                .title(paperTitle)
                .titleNorm(titleNorm)
                .authors(JsonUtils.toJsonString(splitAuthors(authors)))
                .doi(truncate(normalizeDoi(doi), 120))
                .status(GalleryConstants.PAPER_NEW)
                .pdfStatus(GalleryConstants.PDF_NONE)
                .build();
        paperMapper.insert(paper);

        Path dest = Path.of(settingService.workdir(), "workspace", "pdfs", venue,
                String.valueOf(year), paper.getId() + ".pdf").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dest.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[uploadPdf][{}] 保存 PDF 失败：{}", paper.getId(), e.getMessage());
            paperMapper.deleteById(paper.getId());
            throw exception(PAPER_PDF_SAVE_FAILED);
        }

        GalleryPaperDO update = new GalleryPaperDO();
        update.setId(paper.getId());
        update.setPdfStatus(GalleryConstants.PDF_CACHED);
        update.setPdfPath(dest.toString());
        update.setPdfSize(file.getSize());
        update.setPdfTime(LocalDateTime.now());
        paperMapper.updateById(update);
        log.info("[uploadPdf][{}] {} {} -> {}", paper.getId(), venue, year, dest);
        return paper.getId();
    }

    private static List<String> splitAuthors(String authors) {
        List<String> list = new ArrayList<>();
        if (authors == null || authors.isBlank()) {
            return list;
        }
        for (String name : authors.split("[,，;；、\\n]+")) {
            String value = name.trim();
            if (!value.isEmpty()) {
                list.add(value);
            }
        }
        return list;
    }

    private static String normalizeDoi(String doi) {
        if (doi == null || doi.isBlank()) {
            return null;
        }
        return doi.trim().replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public void updatePaper(PaperUpdateReqVO reqVO) {
        validatePaperExists(reqVO.getId());
        GalleryPaperDO update = new GalleryPaperDO();
        update.setId(reqVO.getId());
        update.setTitle(reqVO.getTitle());
        update.setPattern(reqVO.getPattern());
        update.setTier(reqVO.getTier());
        update.setAward(reqVO.getAward());
        update.setScore(reqVO.getScore());
        update.setRemark(reqVO.getRemark());
        paperMapper.updateById(update);
    }

    @Override
    public Map<String, Object> clearAllPapers() {
        Path workdir = Path.of(settingService.workdir()).toAbsolutePath().normalize();
        int pdfFiles = deleteFilesUnder(workdir.resolve("workspace").resolve("pdfs"));
        int cropFiles = deleteFilesUnder(workdir.resolve("workspace").resolve("crops"));
        int crops = cropMapper.deleteAllRows();
        int papers = paperMapper.deleteAllRows();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("papers", papers);
        result.put("crops", crops);
        result.put("pdfFiles", pdfFiles);
        result.put("cropFiles", cropFiles);
        result.put("files", pdfFiles + cropFiles);
        log.info("[clearAllPapers] 已清空论文库：论文 {} 篇、裁剪记录 {} 条、PDF {} 个、裁剪图片 {} 个",
                papers, crops, pdfFiles, cropFiles);
        return result;
    }

    /** 删除目录下的全部文件（含子目录），保留根目录本身，返回删除的文件数 */
    private int deleteFilesUnder(Path base) {
        if (!Files.isDirectory(base)) {
            return 0;
        }
        int deleted = 0;
        try (var stream = Files.walk(base)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            for (Path path : files) {
                try {
                    if (Files.deleteIfExists(path)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    log.warn("[deleteFilesUnder] 删除 {} 失败：{}", path, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("[deleteFilesUnder] 遍历 {} 失败：{}", base, e.getMessage());
        }
        // 清理空目录
        try (var stream = Files.walk(base)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(base) && Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 目录非空时忽略
                        }
                    });
        } catch (IOException ignored) {
            // ignore
        }
        return deleted;
    }

    @Override
    public void deletePaper(Long id) {
        validatePaperExists(id);
        // 删除论文时一并清理原始 PDF / 裁剪图（保留静态站已发布图片与备份）
        try {
            artifactCleanupService.cleanupAfterPublish(List.of(id));
        } catch (Exception e) {
            log.warn("[deletePaper][{}] 清理原始文件失败：{}", id, e.getMessage());
        }
        paperMapper.deleteById(id);
    }

    @Override
    public PageResult<ReviewRespVO> getReviewPage(ReviewPageReqVO reqVO) {
        PageResult<GalleryCropDO> page = cropMapper.selectPage(reqVO);
        List<ReviewRespVO> list = new ArrayList<>(page.getList().size());
        for (GalleryCropDO crop : page.getList()) {
            GalleryPaperDO paper = paperMapper.selectById(crop.getPaperId());
            if (paper == null) {
                continue;
            }
            ReviewRespVO vo = new ReviewRespVO();
            vo.setId(crop.getId());
            vo.setCropId(crop.getId());
            vo.setPaperId(paper.getId());
            vo.setJobId(crop.getJobId());
            vo.setVenue(paper.getVenue());
            vo.setYear(paper.getYear());
            vo.setTitle(paper.getTitle());
            vo.setAuthors(paper.getAuthors());
            vo.setPaperUrl(paper.getPaperUrl());
            vo.setPageNo(crop.getPageNo());
            vo.setDpi(crop.getDpi());
            vo.setWidth(crop.getWidth());
            vo.setHeight(crop.getHeight());
            vo.setConfidence(crop.getConfidence());
            vo.setQuality(crop.getQuality());
            vo.setFlagsJson(crop.getFlagsJson());
            vo.setScoresJson(crop.getScoresJson());
            vo.setQcJson(crop.getQcJson());
            vo.setStatus(crop.getStatus());
            vo.setReviewer(crop.getReviewer());
            vo.setReviewTime(crop.getReviewTime());
            vo.setReviewComment(crop.getReviewComment());
            vo.setAlgorithm(crop.getAlgorithm());
            vo.setPattern(paper.getPattern());
            vo.setTier(paper.getTier());
            vo.setAward(paper.getAward());
            vo.setThumb(Thumbnails.dataUri(crop.getImagePath(), 900));
            list.add(vo);
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public Map<String, Object> getDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Long> byVenue = new LinkedHashMap<>();
        for (Map<String, Object> row : paperMapper.countGroupByVenue()) {
            byVenue.put(String.valueOf(row.get("venue")), ((Number) row.get("cnt")).longValue());
        }
        data.put("byVenue", byVenue);
        data.put("papers", paperMapper.selectCount());
        data.put("pendingReview", cropMapper.countByStatus(GalleryConstants.CROP_PENDING));
        data.put("approved", cropMapper.countByStatus(GalleryConstants.CROP_APPROVED));
        data.put("rejected", cropMapper.countByStatus(GalleryConstants.CROP_REJECTED));
        data.put("published", paperMapper.countByStatus(GalleryConstants.PAPER_PUBLISHED));
        data.put("toCrop", paperMapper.countByStatus(GalleryConstants.PAPER_NEW));
        data.put("runningJobs", galleryJobMapper.countByStatus(GalleryConstants.JOB_RUNNING));
        List<Map<String, Object>> recentJobs = new ArrayList<>();
        for (GalleryJobDO job : galleryJobMapper.selectRecent(8)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", job.getId());
            row.put("kind", job.getKind());
            row.put("status", job.getStatus());
            row.put("venue", job.getVenue());
            row.put("year", job.getYear());
            row.put("progress", job.getProgress());
            row.put("createTime", job.getCreateTime() == null ? null
                    : job.getCreateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            recentJobs.add(row);
        }
        data.put("recentJobs", recentJobs);
        GalleryPublishDO lastPublish = publishMapper.selectLastSuccess();
        data.put("lastPublish", lastPublish);
        data.put("site", siteInfo());
        data.put("system", systemInfo());
        return data;
    }

    /** 静态站现状：figures.json 条数与更新时间 */
    private Map<String, Object> siteInfo() {
        Map<String, Object> site = new LinkedHashMap<>();
        try {
            java.nio.file.Path figures = java.nio.file.Path.of(settingService.siteDir(), "data", "figures.json");
            site.put("dir", settingService.siteDir());
            if (java.nio.file.Files.isRegularFile(figures)) {
                var list = JsonUtils.parseArray(java.nio.file.Files.readString(figures), Map.class);
                site.put("figures", list == null ? 0 : list.size());
                site.put("updatedAt", java.nio.file.Files.getLastModifiedTime(figures).toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            } else {
                site.put("figures", 0);
            }
        } catch (Exception e) {
            site.put("figures", 0);
            site.put("error", e.getMessage());
        }
        return site;
    }

    /** 后台运行环境 */
    private Map<String, Object> systemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        var runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
        var heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long upSeconds = runtime.getUptime() / 1000;
        info.put("java", System.getProperty("java.version"));
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        info.put("cores", Runtime.getRuntime().availableProcessors());
        info.put("heapUsedMb", heap.getUsed() / 1024 / 1024);
        info.put("heapMaxMb", heap.getMax() / 1024 / 1024);
        info.put("uptime", String.format("%d天 %d小时 %d分", upSeconds / 86400, (upSeconds % 86400) / 3600, (upSeconds % 3600) / 60));
        info.put("startedAt", java.time.Instant.ofEpochMilli(runtime.getStartTime())
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return info;
    }

    private static List<String> parseAuthors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> authors = JsonUtils.parseArray(json, String.class);
        return authors == null ? List.of() : authors;
    }
}
