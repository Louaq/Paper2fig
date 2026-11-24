package cn.iocoder.yudao.module.gallery.service.backup;

import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 静态站备份保留策略。
 *
 * 备份目录名：yyyyMMdd-HHmmss-SSS（Java）或 yyyyMMdd-HHmmss（历史 Python）。
 * 策略：最近 keep_min 份永远保留；超过 keep_days 删除；超过 archive_days 先打包为
 * archive/<name>.tar.gz 再删除原目录；总份数不超过 keep_max。
 */
@Service
@Slf4j
public class GalleryBackupServiceImpl implements GalleryBackupService {

    private static final Pattern DIR_NAME = Pattern.compile("^\\d{8}-\\d{6}(-\\d{3})?$");
    private static final DateTimeFormatter WITH_MILLIS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final DateTimeFormatter WITHOUT_MILLIS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Resource
    private GallerySettingService settingService;

    private Path root() {
        return Path.of(settingService.get("backup_dir", "/opt/backups/fig-gallery"));
    }

    @Override
    public Map<String, Object> summary() {
        Path root = root();
        List<Path> dirs = listDirs(root);
        long bytes = dirs.stream().mapToLong(this::directorySize).sum();
        long archiveBytes = listArchives(root).stream().mapToLong(this::directorySize).sum();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("root", root.toString());
        data.put("count", dirs.size());
        data.put("bytes", bytes);
        data.put("archiveCount", listArchives(root).size());
        data.put("archiveBytes", archiveBytes);
        data.put("keepDays", settingService.getInt("backup_keep_days", 30));
        data.put("keepMin", settingService.getInt("backup_keep_min", 20));
        data.put("keepMax", settingService.getInt("backup_keep_max", 200));
        data.put("archiveDays", settingService.getInt("backup_archive_days", 7));
        return data;
    }

    @Override
    public Map<String, Object> cleanup(boolean dryRun) {
        Path root = root();
        List<Path> dirs = listDirs(root);
        dirs.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        int keepDays = settingService.getInt("backup_keep_days", 30);
        int keepMin = settingService.getInt("backup_keep_min", 20);
        int keepMax = settingService.getInt("backup_keep_max", 200);
        int archiveDays = settingService.getInt("backup_archive_days", 7);
        LocalDateTime now = LocalDateTime.now();

        List<String> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        long freed = 0;
        int archived = 0;
        int removed = 0;
        int kept = 0;

        for (int i = 0; i < dirs.size(); i++) {
            Path dir = dirs.get(i);
            String name = dir.getFileName().toString();
            long ageDays = ageDays(name, now);
            boolean protectedByMin = i < keepMin;
            boolean overMax = dirs.size() - removed > keepMax && i >= keepMin;

            if (protectedByMin && !overMax) {
                kept++;
                continue;
            }
            if (ageDays < archiveDays && ageDays < keepDays && !overMax) {
                kept++;
                continue;
            }
            long size = directorySize(dir);
            if (ageDays > keepDays || overMax) {
                actions.add("删除 " + name + "（" + ageDays + " 天，" + human(size) + "）");
                if (!dryRun) {
                    if (deleteRecursively(dir)) {
                        removed++;
                        freed += size;
                    } else {
                        errors.add("删除失败：" + name);
                    }
                } else {
                    removed++;
                    freed += size;
                }
            } else {
                actions.add("归档 " + name + "（" + ageDays + " 天，" + human(size) + "）");
                if (!dryRun) {
                    if (archive(root, dir)) {
                        archived++;
                        freed += size;
                        removed++;
                    } else {
                        errors.add("归档失败：" + name);
                    }
                } else {
                    archived++;
                    removed++;
                    freed += size;
                }
            }
        }

        // 清理过期的归档文件
        for (Path archive : listArchives(root)) {
            long ageDays = ageDays(archive.getFileName().toString().replace(".tar.gz", ""), now);
            if (ageDays > keepDays) {
                long size = directorySize(archive);
                actions.add("删除归档 " + archive.getFileName() + "（" + human(size) + "）");
                if (!dryRun && deleteRecursively(archive)) {
                    freed += size;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("root", root.toString());
        result.put("total", dirs.size());
        result.put("kept", dryRun ? dirs.size() - removed : kept);
        result.put("removed", removed);
        result.put("archived", archived);
        result.put("freedBytes", freed);
        result.put("freed", human(freed));
        result.put("actions", actions);
        result.put("errors", errors);
        log.info("[cleanup] dryRun={} removed={} archived={} freed={}", dryRun, removed, archived, human(freed));
        return result;
    }

    // ------------------------------------------------------------------
    private List<Path> listDirs(Path root) {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> DIR_NAME.matcher(path.getFileName().toString()).matches())
                    .forEach(result::add);
        } catch (IOException e) {
            log.warn("[listDirs] {}: {}", root, e.getMessage());
        }
        return result;
    }

    private List<Path> listArchives(Path root) {
        Path archiveDir = root.resolve("archive");
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(archiveDir)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(archiveDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".tar.gz"))
                    .forEach(result::add);
        } catch (IOException e) {
            log.warn("[listArchives] {}: {}", archiveDir, e.getMessage());
        }
        return result;
    }

    private long ageDays(String name, LocalDateTime now) {
        try {
            LocalDateTime time = name.length() > 15
                    ? LocalDateTime.parse(name, WITH_MILLIS)
                    : LocalDateTime.parse(name, WITHOUT_MILLIS);
            return Math.max(0, Duration.between(time, now).toDays());
        } catch (Exception e) {
            return 0;
        }
    }

    private long directorySize(Path path) {
        if (!Files.exists(path)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException ignored) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private boolean archive(Path root, Path dir) {
        Path archiveDir = root.resolve("archive");
        try {
            Files.createDirectories(archiveDir);
            Path target = archiveDir.resolve(dir.getFileName() + ".tar.gz");
            Process process = new ProcessBuilder("tar", "-czf", target.toString(),
                    "-C", root.toString(), dir.getFileName().toString())
                    .redirectErrorStream(true)
                    .start();
            int exit = process.waitFor();
            if (exit != 0) {
                log.warn("[archive] tar exit {} for {}", exit, dir);
                return false;
            }
            return deleteRecursively(dir);
        } catch (Exception e) {
            log.warn("[archive] {}: {}", dir, e.getMessage());
            return false;
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
