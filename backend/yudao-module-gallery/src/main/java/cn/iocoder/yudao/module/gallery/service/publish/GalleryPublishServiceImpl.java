package cn.iocoder.yudao.module.gallery.service.publish;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gallery.controller.admin.publish.vo.PublishPageReqVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryCropDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryFigureDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPaperDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPublishDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryCropMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryFigureMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryPaperMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryPublishMapper;
import cn.iocoder.yudao.module.gallery.enums.GalleryConstants;
import cn.iocoder.yudao.module.gallery.service.artifacts.GalleryArtifactCleanupService;
import cn.iocoder.yudao.module.gallery.service.search.GallerySearchIndexService;
import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import cn.iocoder.yudao.module.gallery.service.site.GallerySiteFileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.NO_APPROVED_CROP;
import static cn.iocoder.yudao.module.gallery.enums.ErrorCodeConstants.PUBLISH_NOT_EXISTS;

/**
 * 发布：把审核通过的论文合并进 gallery_figure（已发布目录的唯一数据源），
 * 再由 gallery_figure 渲染静态站文件。
 *
 * 人工编辑优先：gallery_figure.manual_edited_time 非空的记录默认跳过，
 * 只有显式 force=true 才允许用论文库覆盖。
 */
@Service
@Validated
@Slf4j
public class GalleryPublishServiceImpl implements GalleryPublishService {

    private static final Set<String> PATTERNS = Set.of(
            "teaser", "conceptual", "framework", "pipeline",
            "architecture", "taxonomy", "results", "comparison");

    @Resource
    private GalleryPaperMapper paperMapper;
    @Resource
    private GalleryCropMapper cropMapper;
    @Resource
    private GalleryPublishMapper publishMapper;
    @Resource
    private GalleryFigureMapper figureMapper;
    @Resource
    private GallerySettingService settingService;
    @Resource
    private GallerySiteFileService siteFileService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private GalleryArtifactCleanupService artifactCleanupService;
    @Resource
    private GallerySearchIndexService searchIndexService;

    @Override
    public PageResult<GalleryPublishDO> getPublishPage(PublishPageReqVO reqVO) {
        return publishMapper.selectPage(reqVO);
    }

    @Override
    public Map<String, Object> summary() {
        Plan plan = buildPlan(false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("approved", plan.approved);
        data.put("ready", plan.items.size());
        data.put("conflicts", plan.conflicts);
        data.put("missing", plan.missing);
        data.put("siteDir", settingService.siteDir());
        return data;
    }

    @Override
    public Map<String, Object> cleanupArtifacts(boolean dryRun) {
        return artifactCleanupService.cleanupPublished(dryRun);
    }

    @Override
    public Map<String, Object> syncSearchIndex() {
        log.info("[syncSearchIndex] 手动重建检索索引…");
        Map<String, Object> result = searchIndexService.syncAll();
        log.info("[syncSearchIndex] 完成：ok={} indexed={} error={}",
                result.get("ok"), result.get("indexed"), result.get("error"));
        return result;
    }

    @Override
    public void deleteRecord(Long id) {
        if (publishMapper.selectById(id) == null) {
            throw exception(PUBLISH_NOT_EXISTS);
        }
        publishMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> preview(boolean force) {
        Plan plan = buildPlan(force);
        Map<String, Object> result = result(plan, true, true);
        record(plan, result, GalleryConstants.PUBLISH_PREVIEW, null);
        return result;
    }

    @Override
    public Map<String, Object> execute(boolean force) {
        Plan plan = buildPlan(force);
        if (plan.approved == 0) {
            throw exception(NO_APPROVED_CROP);
        }
        if (!plan.errors.isEmpty()) {
            Map<String, Object> failed = result(plan, false, false);
            record(plan, failed, GalleryConstants.PUBLISH_FAILED, null);
            return failed;
        }
        if (plan.items.isEmpty()) {
            plan.errors.add("没有可发布的记录（可能全部与人工编辑冲突，可勾选“覆盖人工修改”后重试）");
            Map<String, Object> failed = result(plan, false, false);
            record(plan, failed, GalleryConstants.PUBLISH_FAILED, null);
            return failed;
        }

        Path backup = siteFileService.backup();
        List<String> copiedImages = new ArrayList<>();
        Map<String, Object> render;
        try {
            // 1. 复制裁剪图到静态站（失败会从备份恢复）
            for (PublishItem item : plan.items) {
                int[] size = siteFileService.copyImage(item.cropPath, item.imageRel, backup);
                item.width = size[0];
                item.height = size[1];
                copiedImages.add(item.imageRel);
            }
            // 2. 用「现有目录 + 本次变更」生成静态站文件（写库前先写文件，失败可回滚）
            render = siteFileService.writeCatalog(mergedRecords(plan), backup);
            // 3. 数据库事务：upsert 目录 + 更新论文状态 + 写发布记录
            Map<String, Object> finalResult = result(plan, true, false);
            finalResult.putAll(render);
            Long publishRecordId = transactionTemplate.execute(status -> persist(plan, finalResult, backup));

            // 4. 发布成功后立即清理原始文件（失败不影响发布结果）
            List<Long> paperIds = plan.items.stream().map(item -> item.paperId).toList();
            Map<String, Object> cleanup;
            try {
                cleanup = artifactCleanupService.cleanupAfterPublish(paperIds);
            } catch (Exception cleanupError) {
                log.warn("[execute] 原始文件清理失败：{}", cleanupError.getMessage());
                cleanup = Map.of("error", String.valueOf(cleanupError.getMessage()));
            }
            finalResult.put("cleanup", cleanup);

            // 5. 同步 Meilisearch 检索索引（失败只记录日志，不影响发布结果）
            Map<String, Object> meili = searchIndexService.syncAll();
            finalResult.put("meili", meili);
            if (publishRecordId != null) {
                GalleryPublishDO updateRecord = new GalleryPublishDO();
                updateRecord.setId(publishRecordId);
                updateRecord.setResultJson(JsonUtils.toJsonString(finalResult));
                publishMapper.updateById(updateRecord);
            }
            log.info("[execute] 发布完成：新增 {}，更新 {}，总数 {}，检索索引 {}",
                    plan.added, plan.updated, render.get("total"),
                    Boolean.TRUE.equals(meili.get("ok")) ? "已同步(" + meili.get("indexed") + ")" : "未同步");
            return finalResult;
        } catch (Exception e) {
            log.error("[execute] 发布失败，开始回滚", e);
            siteFileService.restoreImages(backup, copiedImages);
            siteFileService.restoreSiteFiles(backup);
            plan.errors.add(e.getMessage() == null ? "发布失败" : e.getMessage());
            Map<String, Object> failed = result(plan, false, false);
            failed.put("backup", backup.toString());
            record(plan, failed, GalleryConstants.PUBLISH_FAILED, backup);
            return failed;
        }
    }

    // ------------------------------------------------------------------
    private Long persist(Plan plan, Map<String, Object> result, Path backup) {
        for (PublishItem item : plan.items) {
            GalleryFigureDO figure = GalleryFigureDO.builder()
                    .paperId(item.paperId)
                    .venue(item.venue)
                    .year(item.year)
                    .title(item.title)
                    .authors(JsonUtils.toJsonString(item.authors))
                    .pattern(item.pattern)
                    .tier(item.tier)
                    .award(item.award)
                    .image(item.imageRel)
                    .paper(item.paper)
                    .pdfSource(item.pdfSource)
                    .score(item.score)
                    .width(item.width)
                    .height(item.height)
                    .origin("PIPELINE")
                    .build();
            if (item.existingId != null) {
                figure.setId(item.existingId);
                figureMapper.updateAllFields(figure);
                if (plan.force && item.existingManual) {
                    figureMapper.clearManualEdited(item.existingId);
                }
            } else {
                figure.setFigureId(item.figureId);
                figureMapper.insert(figure);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (PublishItem item : plan.items) {
            GalleryPaperDO update = new GalleryPaperDO();
            update.setId(item.paperId);
            update.setStatus(GalleryConstants.PAPER_PUBLISHED);
            update.setPublishedTime(now);
            paperMapper.updateById(update);
        }
        GalleryPublishDO record = GalleryPublishDO.builder()
                .version(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
                .siteDir(settingService.siteDir())
                .added(plan.added)
                .updated(plan.updated)
                .total(asInt(result.get("total")))
                .status(GalleryConstants.PUBLISH_SUCCESS)
                .backupDir(backup == null ? null : backup.toString())
                .resultJson(JsonUtils.toJsonString(result))
                .operatorName(operator())
                .build();
        publishMapper.insert(record);
        return record.getId();
    }

    private void record(Plan plan, Map<String, Object> result, int status, Path backup) {
        GalleryPublishDO record = GalleryPublishDO.builder()
                .version(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
                .siteDir(settingService.siteDir())
                .added(plan.added)
                .updated(plan.updated)
                .total(asInt(result.get("total")))
                .status(status)
                .backupDir(backup == null ? null : backup.toString())
                .resultJson(JsonUtils.toJsonString(result))
                .operatorName(operator())
                .build();
        publishMapper.insert(record);
    }

    private Map<String, Object> result(Plan plan, boolean ok, boolean preview) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok && plan.errors.isEmpty());
        result.put("preview", preview);
        result.put("force", plan.force);
        result.put("incoming", plan.items.size());
        result.put("added", plan.added);
        result.put("updated", plan.updated);
        result.put("total", plan.existingCount + plan.added);
        result.put("totalAfter", plan.existingCount + plan.added);
        result.put("conflicts", plan.conflicts);
        result.put("missing", plan.missing);
        result.put("warnings", plan.warnings);
        result.put("errors", plan.errors);
        result.put("siteDir", settingService.siteDir());
        return result;
    }

    // ------------------------------------------------------------------
    private Plan buildPlan(boolean force) {
        Plan plan = new Plan();
        plan.force = force;
        List<GalleryPaperDO> papers = approvedPapers();
        plan.approved = papers.size();

        Map<String, GalleryFigureDO> existing = new LinkedHashMap<>();
        for (GalleryFigureDO figure : figureMapper.selectListOrdered()) {
            existing.put(figure.getFigureId(), figure);
        }
        plan.existingCount = existing.size();
        Set<String> seen = new HashSet<>();

        for (GalleryPaperDO paper : papers) {
            String figureId = galleryId(paper);
            GalleryCropDO crop = paper.getCropId() == null ? null : cropMapper.selectById(paper.getCropId());
            if (crop == null || crop.getImagePath() == null || !Files.isRegularFile(Path.of(crop.getImagePath()))) {
                plan.missing.add(mapOf("id", paper.getId(), "figureId", figureId,
                        "title", paper.getTitle(), "reason", "裁剪文件缺失"));
                plan.errors.add(figureId + ": 裁剪文件缺失");
                continue;
            }
            PublishItem item = new PublishItem();
            item.paperId = paper.getId();
            item.figureId = figureId;
            item.venue = paper.getVenue() == null ? "" : paper.getVenue().toLowerCase();
            item.year = paper.getYear() == null ? 0 : paper.getYear();
            item.title = paper.getTitle() == null ? "" : paper.getTitle().trim();
            item.authors = JsonUtils.parseArray(paper.getAuthors() == null ? "[]" : paper.getAuthors(), String.class);
            item.pattern = paper.getPattern() == null || paper.getPattern().isBlank() ? "teaser" : paper.getPattern();
            item.tier = blankToNull(paper.getTier());
            item.award = blankToNull(paper.getAward());
            item.score = paper.getScore();
            item.paper = paper.getPaperUrl();
            item.pdfSource = paper.getPdfUrl() == null ? paper.getPaperUrl() : paper.getPdfUrl();
            item.cropPath = crop.getImagePath();
            item.imageRel = "images/" + item.venue + "/final/" + figureId + ".jpg";

            if (!GalleryConstants.VENUES.contains(item.venue)) {
                plan.errors.add(figureId + ": 不支持的会议 " + item.venue);
                continue;
            }
            if (item.year < 1900 || item.year > 2100) {
                plan.errors.add(figureId + ": 年份不合法");
                continue;
            }
            if (item.title.isEmpty()) {
                plan.errors.add(figureId + ": 标题为空");
                continue;
            }
            if (item.authors == null || item.authors.isEmpty()) {
                plan.errors.add(figureId + ": 作者为空");
                continue;
            }
            if (item.paper == null || item.paper.isBlank()) {
                plan.errors.add(figureId + ": 论文链接为空");
                continue;
            }
            if (!PATTERNS.contains(item.pattern)) {
                plan.warnings.add(figureId + ": pattern " + item.pattern + " 已重置为 teaser");
                item.pattern = "teaser";
            }
            if (item.tier != null && !Set.of("oral", "spotlight").contains(item.tier)) {
                plan.warnings.add(figureId + ": tier " + item.tier + " 已忽略");
                item.tier = null;
            }
            if (item.award != null && !Set.of("best", "honorable").contains(item.award)) {
                plan.warnings.add(figureId + ": award " + item.award + " 已忽略");
                item.award = null;
            }
            if (!seen.add(figureId)) {
                plan.errors.add(figureId + ": 同批次存在重复 ID");
                continue;
            }
            try {
                BufferedImage image = ImageIO.read(Path.of(item.cropPath).toFile());
                if (image == null) {
                    plan.errors.add(figureId + ": 无法读取裁剪图片");
                    continue;
                }
                item.width = image.getWidth();
                item.height = image.getHeight();
                if (item.width < 200 || item.height < 100) {
                    plan.errors.add(figureId + ": 图片尺寸过小 " + item.width + "×" + item.height);
                    continue;
                }
            } catch (Exception e) {
                plan.errors.add(figureId + ": 无法读取裁剪图片");
                continue;
            }

            GalleryFigureDO old = existing.get(figureId);
            if (old != null && old.getManualEditedTime() != null && !force) {
                plan.conflicts.add(mapOf("id", figureId, "paperId", paper.getId(),
                        "title", paper.getTitle(), "manualEditedTime", String.valueOf(old.getManualEditedTime())));
                continue;
            }
            item.existingId = old == null ? null : old.getId();
            item.existingManual = old != null && old.getManualEditedTime() != null;
            if (old == null) {
                plan.added++;
            } else {
                plan.updated++;
            }
            plan.items.add(item);
        }
        return plan;
    }

    /** 现有目录 + 本次变更，用于写 figures.json/figures.js 的完整快照。 */
    private List<Map<String, Object>> mergedRecords(Plan plan) {
        List<Map<String, Object>> records = siteFileService.catalogRecords();
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            index.put(String.valueOf(records.get(i).get("id")), i);
        }
        for (PublishItem item : plan.items) {
            Map<String, Object> record = item.toRecord();
            Integer position = index.get(item.figureId);
            if (position != null) {
                records.set(position, record);
            } else {
                index.put(item.figureId, records.size());
                records.add(record);
            }
        }
        return records;
    }

    private List<GalleryPaperDO> approvedPapers() {
        return paperMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GalleryPaperDO>()
                .eq(GalleryPaperDO::getStatus, GalleryConstants.PAPER_APPROVED)
                .isNotNull(GalleryPaperDO::getCropId)
                .orderByAsc(GalleryPaperDO::getVenue)
                .orderByAsc(GalleryPaperDO::getYear)
                .orderByAsc(GalleryPaperDO::getId));
    }

    private String galleryId(GalleryPaperDO paper) {
        String sourceId = paper.getSourceId() == null ? String.valueOf(paper.getId()) : paper.getSourceId();
        String safe = sourceId.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (safe.length() > 90) {
            safe = safe.substring(0, 90);
        }
        return paper.getVenue() + paper.getYear() + "-" + safe;
    }

    private static String operator() {
        String nickname = SecurityFrameworkUtils.getLoginUserNickname();
        return nickname == null ? "system" : nickname;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer asInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private static class Plan {
        boolean force;
        int approved;
        int existingCount;
        int added;
        int updated;
        List<PublishItem> items = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
    }

    private static class PublishItem {
        Long paperId;
        String figureId;
        Long existingId;
        boolean existingManual;
        String venue;
        int year;
        String title;
        List<String> authors;
        String pattern;
        String tier;
        String award;
        Double score;
        String paper;
        String pdfSource;
        String cropPath;
        String imageRel;
        int width;
        int height;

        Map<String, Object> toRecord() {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", figureId);
            record.put("venue", venue);
            record.put("year", year);
            record.put("title", title);
            record.put("authors", authors);
            record.put("pattern", pattern);
            record.put("image", imageRel);
            record.put("paper", paper);
            record.put("pdf_source", pdfSource);
            if (score != null) {
                record.put("score", score);
            }
            record.put("w", width);
            record.put("h", height);
            if (tier != null) {
                record.put("tier", tier);
            }
            if (award != null) {
                record.put("award", award);
            }
            return record;
        }
    }
}
