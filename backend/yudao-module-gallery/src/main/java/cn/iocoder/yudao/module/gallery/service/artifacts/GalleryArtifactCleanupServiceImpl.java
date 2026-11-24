package cn.iocoder.yudao.module.gallery.service.artifacts;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryCropDO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryPaperDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryCropMapper;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryPaperMapper;
import cn.iocoder.yudao.module.gallery.enums.GalleryConstants;
import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 发布成功后立即清理原始文件：
 * - 论文 PDF（workspace/pdfs/<venue>/<year>/<paperId>.pdf 或数据库 pdf_path）
 * - 工作区裁剪图（gallery_crop.image_path）
 * - 发布导出目录 / 发布日志（workspace/jobs/publish-*）
 * - 当某个裁剪任务的所有论文都已发布时，删除该任务目录（results.jsonl / job.log）
 *
 * 静态站已发布图片与 /opt/backups/fig-gallery 不在此范围。
 */
@Service
@Slf4j
public class GalleryArtifactCleanupServiceImpl implements GalleryArtifactCleanupService {

    @Resource
    private GalleryPaperMapper paperMapper;
    @Resource
    private GalleryCropMapper cropMapper;
    @Resource
    private GallerySettingService settingService;

    @Override
    public Map<String, Object> cleanupPublished(boolean dryRun) {
        List<GalleryPaperDO> papers = paperMapper.selectPublishedOrDeleted();
        List<GalleryCropDO> crops = new ArrayList<>();
        for (GalleryPaperDO paper : papers) {
            if (paper.getCropId() != null) {
                GalleryCropDO crop = cropMapper.selectByIdIncludeDeleted(paper.getCropId());
                if (crop != null) {
                    crops.add(crop);
                }
            }
        }
        return clean(papers, crops, dryRun);
    }

    @Override
    public Map<String, Object> cleanupAfterPublish(Collection<Long> paperIds) {
        List<GalleryPaperDO> papers = paperIds == null || paperIds.isEmpty()
                ? List.of() : paperMapper.selectListByIds(paperIds);
        List<GalleryCropDO> crops = new ArrayList<>();
        for (GalleryPaperDO paper : papers) {
            if (paper.getCropId() != null) {
                GalleryCropDO crop = cropMapper.selectById(paper.getCropId());
                if (crop != null) {
                    crops.add(crop);
                }
            }
        }
        return clean(papers, crops, false);
    }

    // ------------------------------------------------------------------
    private Map<String, Object> clean(List<GalleryPaperDO> papers, List<GalleryCropDO> crops, boolean dryRun) {
        Path workspace = Path.of(settingService.workdir(), "workspace").toAbsolutePath().normalize();
        Set<Path> emptyDirs = new LinkedHashSet<>();
        List<String> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        long pdfBytes = 0;
        long cropBytes = 0;
        int pdfFiles = 0;
        int cropFiles = 0;

        for (GalleryPaperDO paper : papers) {
            boolean pdfCleared = true;
            for (Path pdf : pdfCandidates(workspace, paper)) {
                if (!safeUnder(workspace, pdf)) {
                    continue;
                }
                if (!Files.isRegularFile(pdf)) {
                    emptyDirs.add(pdf.getParent());
                    continue;
                }
                long size = fileSize(pdf);
                if (dryRun) {
                    pdfFiles++;
                    pdfBytes += size;
                    actions.add("删除 PDF " + pdf);
                } else {
                    try {
                        Files.delete(pdf);
                        pdfFiles++;
                        pdfBytes += size;
                        actions.add("删除 PDF " + pdf);
                        emptyDirs.add(pdf.getParent());
                    } catch (IOException e) {
                        // 删除失败时保留数据库路径，方便下次手动清理重试
                        pdfCleared = false;
                        errors.add("删除 PDF 失败 " + pdf + "：" + e.getMessage());
                    }
                }
            }
            if (!dryRun && pdfCleared) {
                paperMapper.clearPdfArtifact(paper.getId());
            }
        }

        for (GalleryCropDO crop : crops) {
            String imagePath = crop.getImagePath();
            if (imagePath == null || imagePath.isBlank()) {
                // 已发布/已删除论文的裁剪记录不再需要出现在审核队列
                if (!dryRun) {
                    cropMapper.deleteById(crop.getId());
                }
                continue;
            }
            Path image = Path.of(imagePath).toAbsolutePath().normalize();
            if (!safeUnder(workspace, image)) {
                if (!dryRun) {
                    cropMapper.clearImagePath(crop.getId());
                }
                continue;
            }
            if (!Files.isRegularFile(image)) {
                emptyDirs.add(image.getParent());
                if (!dryRun) {
                    cropMapper.clearImagePath(crop.getId());
                    cropMapper.deleteById(crop.getId());
                }
                continue;
            }
            long size = fileSize(image);
            if (dryRun) {
                cropFiles++;
                cropBytes += size;
                actions.add("删除裁剪图 " + image);
            } else {
                try {
                    Files.delete(image);
                    cropFiles++;
                    cropBytes += size;
                    actions.add("删除裁剪图 " + image);
                    emptyDirs.add(image.getParent());
                    cropMapper.clearImagePath(crop.getId());
                    cropMapper.deleteById(crop.getId());
                } catch (IOException e) {
                    // 删除失败时保留数据库路径，方便下次手动清理重试
                    errors.add("删除裁剪图失败 " + image + "：" + e.getMessage());
                }
            }
        }

        // 发布导出目录 / 发布日志（历史 Python 发布遗留）
        Path jobsDir = workspace.resolve("jobs");
        int publishDirs = 0;
        if (Files.isDirectory(jobsDir)) {
            try (Stream<Path> stream = Files.list(jobsDir)) {
                for (Path dir : stream.filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith("publish-")).toList()) {
                    long size = directorySize(dir);
                    publishDirs++;
                    actions.add("删除发布目录 " + dir.getFileName() + "（" + human(size) + "）");
                    if (!dryRun && !deleteRecursively(dir)) {
                        errors.add("删除发布目录失败：" + dir);
                    }
                }
            } catch (IOException e) {
                errors.add("扫描发布目录失败：" + e.getMessage());
            }
        }

        // 裁剪任务日志：该任务下的所有论文都已发布时才删
        int jobDirs = 0;
        Set<Long> jobIds = new LinkedHashSet<>();
        for (GalleryCropDO crop : crops) {
            if (crop.getJobId() != null) {
                jobIds.add(crop.getJobId());
            }
        }
        for (Long jobId : jobIds) {
            List<GalleryCropDO> jobCrops = cropMapper.selectListByJobId(jobId);
            boolean allPublished = !jobCrops.isEmpty();
            for (GalleryCropDO jobCrop : jobCrops) {
                GalleryPaperDO paper = paperMapper.selectById(jobCrop.getPaperId());
                if (paper != null && !Integer.valueOf(GalleryConstants.PAPER_PUBLISHED).equals(paper.getStatus())) {
                    allPublished = false;
                    break;
                }
            }
            Path jobDir = jobsDir.resolve(String.valueOf(jobId));
            if (allPublished && Files.isDirectory(jobDir)) {
                jobDirs++;
                actions.add("删除任务日志 " + jobDir.getFileName());
                if (!dryRun && !deleteRecursively(jobDir)) {
                    errors.add("删除任务日志失败：" + jobDir);
                }
            }
        }

        if (!dryRun) {
            for (Path dir : emptyDirs) {
                deleteEmptyParents(dir, workspace);
            }
            // 清理 pdfs/crops 下因文件删除而变空的遗留目录（jobs 目录不动）
            pruneEmptyDirs(workspace.resolve("pdfs"));
            pruneEmptyDirs(workspace.resolve("crops"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("publishedPapers", papers.size());
        result.put("pdfFiles", pdfFiles);
        result.put("pdfBytes", pdfBytes);
        result.put("cropFiles", cropFiles);
        result.put("cropBytes", cropBytes);
        result.put("publishDirs", publishDirs);
        result.put("jobDirs", jobDirs);
        result.put("freedBytes", pdfBytes + cropBytes);
        result.put("freed", human(pdfBytes + cropBytes));
        result.put("actions", actions.size() > 200 ? actions.subList(0, 200) : actions);
        result.put("errors", errors);
        log.info("[clean] {}", JsonUtils.toJsonString(result));
        return result;
    }

    private List<Path> pdfCandidates(Path workspace, GalleryPaperDO paper) {
        Set<Path> paths = new LinkedHashSet<>();
        if (paper.getPdfPath() != null && !paper.getPdfPath().isBlank()) {
            paths.add(Path.of(paper.getPdfPath()).toAbsolutePath().normalize());
        }
        // 兜底：fetch/crop 默认缓存路径 workspace/pdfs/<venue>/<year>/<paperId>.pdf
        paths.add(workspace.resolve("pdfs")
                .resolve(paper.getVenue() == null ? "" : paper.getVenue())
                .resolve(paper.getYear() == null ? "" : String.valueOf(paper.getYear()))
                .resolve(paper.getId() + ".pdf").toAbsolutePath().normalize());
        return new ArrayList<>(paths);
    }

    private boolean safeUnder(Path root, Path path) {
        return path != null && path.normalize().startsWith(root);
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private long directorySize(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile).mapToLong(this::fileSize).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** 自底向上删除 root 下所有空目录（root 本身为空时也删除）。 */
    private void pruneEmptyDirs(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path dir : stream.sorted(Comparator.reverseOrder()).toList()) {
                try (Stream<Path> entries = Files.list(dir)) {
                    if (entries.findAny().isPresent()) {
                        continue;
                    }
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            log.warn("[pruneEmptyDirs] {}: {}", root, e.getMessage());
        }
    }

    private void deleteEmptyParents(Path dir, Path workspace) {        Path current = dir;
        while (current != null && current.startsWith(workspace) && !current.equals(workspace)) {
            try (Stream<Path> entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            } catch (IOException e) {
                return;
            }
            try {
                Files.deleteIfExists(current);
            } catch (IOException e) {
                return;
            }
            current = current.getParent();
        }
    }

    private boolean deleteRecursively(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            return true;
        } catch (Exception e) {
            log.warn("[deleteRecursively] {}: {}", path, e.getMessage());
            return false;
        }
    }

    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        }
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }
}
