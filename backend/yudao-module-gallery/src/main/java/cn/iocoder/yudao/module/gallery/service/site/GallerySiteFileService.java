package cn.iocoder.yudao.module.gallery.service.site;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryFigureDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryFigureMapper;
import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 静态画廊文件读写：figures.json / figures.js / sitemap.xml / 图片文件。
 *
 * 2026-11-23 起 gallery_figure 是「已发布目录」的唯一数据源，本类负责把数据库
 * 渲染成静态站文件；所有写操作先备份，再用「临时文件 + rename」原子替换。
 */
@Service
@Slf4j
public class GallerySiteFileService {

    @Resource
    private GallerySettingService settingService;
    @Resource
    private GalleryFigureMapper figureMapper;

    public Path siteDir() {
        return Path.of(settingService.siteDir());
    }

    /** Public site URL used for sitemap entries; override with PUBLIC_BASE_URL. */
    private String baseUrl() {
        String url = System.getenv("PUBLIC_BASE_URL");
        if (url == null || url.isBlank()) {
            url = settingService.get("site_base_url", "http://localhost:8080/");
        }
        return url.endsWith("/") ? url : url + "/";
    }

    public Path figuresJson() {
        return siteDir().resolve("data/figures.json");
    }

    public Path figuresJs() {
        return siteDir().resolve("assets/figures.js");
    }

    public Path imageFile(String imageRel) {
        Path root = siteDir().resolve("images").normalize();
        Path target = siteDir().resolve(imageRel).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的图片路径：" + imageRel);
        }
        return target;
    }

    // ------------------------------------------------------------------
    // 读取 / 渲染
    // ------------------------------------------------------------------

    /** 把 gallery_figure 渲染为 figures.json / figures.js / sitemap.xml。 */
    public synchronized Map<String, Object> renderSite() {
        Path backup = backup();
        return renderSite(backup);
    }

    /** 渲染到静态站；调用方已创建备份时传入 backupDir，失败时用备份回滚。 */
    public synchronized Map<String, Object> renderSite(Path backupDir) {
        Map<String, Object> result = writeCatalog(catalogRecords(), backupDir);
        log.info("[renderSite] 已渲染 {} 条图片，备份 {}", result.get("total"), backupDir);
        return result;
    }

    /** 把给定的记录列表写入静态站（发布时在数据库提交前写文件，便于失败回滚）。 */
    public synchronized Map<String, Object> writeCatalog(List<Map<String, Object>> figures, Path backupDir) {
        try {
            atomicWrite(figuresJson(), JsonUtils.toJsonPrettyString(figures) + "\n");
            atomicWrite(figuresJs(), "window.FIGURES = " + JsonUtils.toJsonString(figures) + ";\n");
            int sitemapEntries = writeSitemap(figures);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", figures.size());
            result.put("sitemapEntries", sitemapEntries);
            result.put("backup", backupDir == null ? null : backupDir.toString());
            result.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            return result;
        } catch (Exception e) {
            restoreSiteFiles(backupDir);
            throw new IllegalStateException("渲染静态站失败，已尝试从备份恢复", e);
        }
    }

    /** 从数据库生成静态站数据结构，字段顺序与历史 figures.json 保持一致。 */
    public List<Map<String, Object>> catalogRecords() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (GalleryFigureDO figure : figureMapper.selectListOrdered()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", figure.getFigureId());
            record.put("venue", figure.getVenue());
            record.put("year", figure.getYear());
            record.put("title", figure.getTitle());
            record.put("authors", JsonUtils.parseArray(
                    figure.getAuthors() == null ? "[]" : figure.getAuthors(), String.class));
            record.put("pattern", figure.getPattern() == null || figure.getPattern().isBlank()
                    ? "teaser" : figure.getPattern());
            record.put("image", figure.getImage());
            record.put("paper", figure.getPaper());
            record.put("pdf_source", figure.getPdfSource() == null ? figure.getPaper() : figure.getPdfSource());
            if (figure.getScore() != null) {
                record.put("score", figure.getScore());
            }
            record.put("w", figure.getWidth() == null ? 0 : figure.getWidth());
            record.put("h", figure.getHeight() == null ? 0 : figure.getHeight());
            if (figure.getTier() != null && !figure.getTier().isBlank()) {
                record.put("tier", figure.getTier());
            }
            if (figure.getAward() != null && !figure.getAward().isBlank()) {
                record.put("award", figure.getAward());
            }
            list.add(record);
        }
        return list;
    }

    private int writeSitemap(List<Map<String, Object>> figures) throws IOException {
        String today = java.time.LocalDate.now().toString();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        xml.append("  <url>\n    <loc>").append(baseUrl()).append("</loc>\n")
                .append("    <lastmod>").append(today).append("</lastmod>\n")
                .append("    <changefreq>daily</changefreq>\n    <priority>1.0</priority>\n  </url>\n");
        for (Map<String, Object> item : figures) {
            xml.append("  <url>\n    <loc>").append(baseUrl()).append("#f=").append(item.get("id")).append("</loc>\n")
                    .append("    <lastmod>").append(today).append("</lastmod>\n")
                    .append("    <changefreq>monthly</changefreq>\n    <priority>0.6</priority>\n  </url>\n");
        }
        if (Files.isRegularFile(siteDir().resolve("support.html"))) {
            xml.append("  <url>\n    <loc>").append(baseUrl()).append("support.html</loc>\n")
                    .append("    <lastmod>").append(today).append("</lastmod>\n")
                    .append("    <changefreq>monthly</changefreq>\n    <priority>0.3</priority>\n  </url>\n");
        }
        xml.append("</urlset>\n");
        atomicWrite(siteDir().resolve("sitemap.xml"), xml.toString());
        return figures.size();
    }

    // ------------------------------------------------------------------
    // 备份 / 恢复
    // ------------------------------------------------------------------

    /**
     * 自动备份已关闭（2026-11-24）：不再创建静态站快照，返回 null。
     * 调用方对 null 均已做保护，发布失败时不会自动回滚文件。
     */
    public Path backup() {
        return null;
    }

    /** 渲染失败时把三个 JSON/JS/sitemap 文件恢复回去（图片由调用方恢复）。 */
    public synchronized void restoreSiteFiles(Path backupDir) {
        if (backupDir == null || !Files.isDirectory(backupDir)) {
            return;
        }
        try {
            for (String rel : List.of("data/figures.json", "assets/figures.js", "sitemap.xml")) {
                Path src = backupDir.resolve(Path.of(rel).getFileName());
                if (Files.isRegularFile(src)) {
                    Files.copy(src, siteDir().resolve(rel), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            log.error("[restoreSiteFiles] 恢复失败：{}", e.getMessage());
        }
    }

    /** 发布失败时恢复被覆盖的图片。 */
    public synchronized void restoreImages(Path backupDir, Collection<String> imageRels) {
        if (backupDir == null || imageRels == null) {
            return;
        }
        for (String rel : imageRels) {
            try {
                Path backup = backupDir.resolve(Path.of(rel).getFileName());
                if (Files.isRegularFile(backup)) {
                    Files.copy(backup, imageFile(rel), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.warn("[restoreImages] 恢复 {} 失败：{}", rel, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // 图片
    // ------------------------------------------------------------------

    /**
     * 把裁剪产物复制到静态站 images/<venue>/final/<id>.jpg，返回宽高。
     * 原图片会备份到 backupDir（按文件名），便于失败回滚。
     */
    public synchronized int[] copyImage(String sourcePath, String imageRel, Path backupDir) {
        Path source = Path.of(sourcePath);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("裁剪图片不存在：" + sourcePath);
        }
        Path target = imageFile(imageRel);
        try {
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) {
                throw new IllegalArgumentException("无法识别的图片：" + sourcePath);
            }
            if (image.getWidth() < 200 || image.getHeight() < 100) {
                throw new IllegalArgumentException("图片尺寸过小（至少 200×100）：" + sourcePath);
            }
            if (Files.isRegularFile(target) && backupDir != null) {
                Files.copy(target, backupDir.resolve(target.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            throw new IllegalStateException("复制图片失败：" + sourcePath, e);
        }
    }

    /** 删除图片文件（先备份到最近的备份目录）。 */
    public synchronized void deleteImage(String imageRel, Path backupDir) {
        Path target = imageFile(imageRel);
        try {
            if (!Files.isRegularFile(target)) {
                return;
            }
            if (backupDir != null) {
                Files.copy(target, backupDir.resolve(target.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.delete(target);
        } catch (IOException e) {
            throw new IllegalStateException("删除图片文件失败", e);
        }
    }

    /** 用上传的图片替换静态站图片。统一转 JPEG（透明背景填白），返回新的宽高。 */
    public synchronized int[] replaceImage(String imageRel, byte[] content, Path backupDir) {
        Path target = imageFile(imageRel);
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                throw new IllegalArgumentException("无法识别的图片文件");
            }
            if (source.getWidth() < 200 || source.getHeight() < 100) {
                throw new IllegalArgumentException("图片尺寸过小（至少 200×100）");
            }
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(source, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            if (Files.isRegularFile(target) && backupDir != null) {
                Files.copy(target, backupDir.resolve(target.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            ImageIO.write(rgb, "jpg", tmp.toFile());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new int[]{rgb.getWidth(), rgb.getHeight()};
        } catch (IOException e) {
            throw new IllegalStateException("保存图片失败", e);
        }
    }

    // ------------------------------------------------------------------
    // 前端筛选项配置
    // ------------------------------------------------------------------

    /** 把筛选项配置写到静态站，供前端 app.js 读取。 */
    public synchronized void saveSiteConfig(Map<String, Object> config) {
        try {
            Path dir = siteDir();
            Files.createDirectories(dir.resolve("assets"));
            Files.createDirectories(dir.resolve("data"));
            atomicWrite(dir.resolve("data/site-config.json"), JsonUtils.toJsonPrettyString(config) + "\n");
            atomicWrite(dir.resolve("assets/site-config.js"),
                    "window.SITE_CONFIG = " + JsonUtils.toJsonString(config) + ";\n");
        } catch (IOException e) {
            throw new IllegalStateException("写入前端配置失败", e);
        }
    }

    public Map<String, Object> buildConfig(List<Map<String, Object>> venues, List<Map<String, Object>> years,
                                           List<Map<String, Object>> tiers, List<Map<String, Object>> patterns) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("venues", venues);
        config.put("years", years);
        config.put("tiers", tiers);
        config.put("patterns", patterns);
        return config;
    }

    private void atomicWrite(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
