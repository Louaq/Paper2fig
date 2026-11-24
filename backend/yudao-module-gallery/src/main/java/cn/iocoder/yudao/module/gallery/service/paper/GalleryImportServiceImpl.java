package cn.iocoder.yudao.module.gallery.service.paper;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryCropDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPaperDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryCropMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryPaperMapper;
import cn.iocoder.yudao.module.gallery.enums.GalleryConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把管线的 JSONL 产物导入 MySQL。所有去重都在这里完成，管线保持无状态。
 */
@Service
@Slf4j
public class GalleryImportServiceImpl implements GalleryImportService {

    @Resource
    private GalleryPaperMapper paperMapper;
    @Resource
    private GalleryCropMapper cropMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int[] importCrawl(Long jobId, String filePath) {
        int added = 0, duplicated = 0, failed = 0, revived = 0;
        for (Map<String, Object> row : readJsonl(filePath)) {
            try {
                Map<String, Object> item = row;
                String source = str(item.get("source"));
                String sourceId = str(item.get("source_id"));
                String venue = str(item.get("venue")).toLowerCase();
                String title = str(item.get("title"));
                String titleNorm = normalizeTitle(title);
                if (title.isBlank()) {
                    failed++;
                    continue;
                }
                List<String> authors = new ArrayList<>();
                Object rawAuthors = item.get("authors");
                if (rawAuthors instanceof List<?> list) {
                    list.forEach(a -> authors.add(String.valueOf(a)));
                }
                GalleryPaperDO paper = GalleryPaperDO.builder()
                        .venue(venue)
                        .year(asInt(item.get("year")))
                        .source(source)
                        .sourceId(sourceId)
                        .dedupeKey(truncate(source + ":" + sourceId + "|title:" + titleNorm, 250))
                        .title(title)
                        .titleNorm(titleNorm)
                        .authors(JsonUtils.toJsonString(authors))
                        .abstractText(truncate(str(item.get("abstract")), 20000))
                        .paperUrl(truncate(str(item.get("paper_url")), 500))
                        .pdfUrl(truncate(str(item.get("pdf_url")), 500))
                        .doi(truncate(str(item.get("doi")), 120))
                        .arxivId(truncate(str(item.get("arxiv_id")), 60))
                        .decision(truncate(str(item.get("decision")), 30))
                        .track(truncate(str(item.get("track")), 120))
                        .status(GalleryConstants.PAPER_NEW)
                        .pdfStatus(GalleryConstants.PDF_NONE)
                        .build();
                // 唯一键包含逻辑删除行，需连 deleted=1 的记录一起查；否则重新采集会撞键。
                GalleryPaperDO existing = paperMapper.selectBySourceIgnoreDeleted(source, sourceId);
                if (existing == null) {
                    existing = paperMapper.selectByTitleNormIgnoreDeleted(venue, titleNorm);
                }
                if (existing != null) {
                    if (Boolean.TRUE.equals(existing.getDeleted())) {
                        paper.setId(existing.getId());
                        paperMapper.revive(paper);
                        revived++;
                    }
                    duplicated++;
                    continue;
                }
                paperMapper.insert(paper);
                added++;
            } catch (DuplicateKeyException e) {
                duplicated++;
                log.info("[importCrawl][job {}] duplicate skipped: {}", jobId, e.getMostSpecificCause().getMessage());
            } catch (Exception e) {
                failed++;
                log.warn("[importCrawl][job {}] bad row: {}", jobId, e.getMessage());
            }
        }
        log.info("[importCrawl][job {}] added={} duplicated={} revived={} failed={}", jobId, added, duplicated, revived, failed);
        return new int[]{added, duplicated, failed};
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int[] importCrop(Long jobId, String filePath) {
        int ok = 0, failed = 0;
        for (Map<String, Object> row : readJsonl(filePath)) {
            try {
                Long paperId = asLong(row.get("id"));
                GalleryPaperDO paper = paperId == null ? null : paperMapper.selectById(paperId);
                if (paper == null) {
                    failed++;
                    continue;
                }
                if (!Boolean.TRUE.equals(row.get("ok"))) {
                    GalleryPaperDO upd = new GalleryPaperDO();
                    upd.setId(paper.getId());
                    upd.setPdfError(truncate(str(row.get("error")), 500));
                    upd.setPdfStatus(GalleryConstants.PDF_FAILED);
                    paperMapper.updateById(upd);
                    failed++;
                    continue;
                }
                Map<String, Object> crop = map(row.get("crop"));
                Map<String, Object> qc = map(row.get("qc"));
                Map<String, Object> pdf = map(row.get("pdf"));
                String imagePath = str(crop.get("crop_path"));
                long size = 0;
                try {
                    size = Files.size(Path.of(imagePath));
                } catch (Exception ignored) {
                }
                GalleryCropDO entity = GalleryCropDO.builder()
                        .paperId(paper.getId())
                        .jobId(jobId)
                        .pageNo(asInt(crop.get("page")))
                        .rect(JsonUtils.toJsonString(crop.get("rect")))
                        .dpi(asInt(crop.get("dpi")))
                        .width(asInt(crop.get("width")))
                        .height(asInt(crop.get("height")))
                        .fileSize(size)
                        .imagePath(imagePath)
                        .confidence(asDouble(crop.get("confidence")))
                        .quality(asDouble(qc.get("quality")))
                        .scoresJson(JsonUtils.toJsonString(crop.get("scores")))
                        .flagsJson(JsonUtils.toJsonString(crop.get("flags")))
                        .qcJson(JsonUtils.toJsonString(qc))
                        .phash(truncate(str(qc.get("dhash")), 64))
                        .status(GalleryConstants.CROP_PENDING)
                        .algorithm(truncate(str(crop.get("algorithm")), 60))
                        .diagnostics(truncate(JsonUtils.toJsonString(crop.get("diagnostics")), 60000))
                        .build();
                cropMapper.insert(entity);

                GalleryPaperDO upd = new GalleryPaperDO();
                upd.setId(paper.getId());
                upd.setStatus(GalleryConstants.PAPER_CROPPED);
                upd.setCropId(entity.getId());
                upd.setPdfStatus(GalleryConstants.PDF_CACHED);
                upd.setPdfPath(truncate(str(row.get("pdf_path")), 500));
                upd.setPdfPages(asInt(pdf.get("pages")));
                upd.setPdfSize(asLong(pdf.get("size")));
                upd.setPdfError(null);
                paperMapper.updateById(upd);
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("[importCrop][job {}] bad row: {}", jobId, e.getMessage());
            }
        }
        log.info("[importCrop][job {}] ok={} failed={}", jobId, ok, failed);
        return new int[]{ok, failed};
    }

    @Override
    public int[] importFetch(Long jobId, String filePath) {
        int ok = 0, failed = 0;
        for (Map<String, Object> row : readJsonl(filePath)) {
            Long paperId = asLong(row.get("id"));
            GalleryPaperDO paper = paperId == null ? null : paperMapper.selectById(paperId);
            if (paper == null) {
                failed++;
                continue;
            }
            GalleryPaperDO upd = new GalleryPaperDO();
            upd.setId(paperId);
            Map<String, Object> pdf = map(row.get("pdf"));
            if (Boolean.TRUE.equals(row.get("ok"))) {
                upd.setPdfStatus(GalleryConstants.PDF_CACHED);
                upd.setPdfPath(truncate(str(pdf.get("path")), 500));
                upd.setPdfPages(asInt(pdf.get("pages")));
                upd.setPdfSize(asLong(pdf.get("size")));
                upd.setPdfTime(LocalDateTime.now());
                ok++;
            } else {
                upd.setPdfStatus(GalleryConstants.PDF_FAILED);
                upd.setPdfError(truncate(str(row.get("error")), 500));
                failed++;
            }
            paperMapper.updateById(upd);
        }
        return new int[]{ok, failed};
    }

    // ------------------------------------------------------------------
    private List<Map<String, Object>> readJsonl(String filePath) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Path path = Path.of(filePath);
        if (!Files.isRegularFile(path)) {
            return rows;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> row = JsonUtils.parseObjectQuietly(line, Map.class);
                if (row != null) {
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            log.error("[readJsonl][{}] failed", filePath, e);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    static String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase()
                .replaceAll("[^0-9a-z\\u4e00-\\u9fff]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
