package cn.iocoder.yudao.module.gallery.service.job;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryJobDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryJobMapper;
import cn.iocoder.yudao.module.gallery.enums.GalleryConstants;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动 gallery-pipeline 子进程，把 stdout 落盘并把 @@PROGRESS 写回数据库。
 *
 * 约定：管线每处理一条就打印一行 {@code @@PROGRESS {...}}，结束时打印
 * {@code @@RESULT {...}}；本类只依赖这两条约定，其余输出原样进日志。
 */
@Component
@Slf4j
public class PipelineExecutor {

    public interface FinishCallback {
        void onFinish(GalleryJobDO job, int exitCode, Map<String, Object> result, String error);
    }

    @Resource
    private GalleryJobMapper galleryJobMapper;
    @Resource
    private cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService settingService;
    @Resource
    private GalleryJobNotifier galleryJobNotifier;

    private final ExecutorService pool = Executors.newFixedThreadPool(2, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "gallery-pipeline-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private final Map<Long, Process> running = new ConcurrentHashMap<>();
    private final Map<Long, StringBuilder> tails = new ConcurrentHashMap<>();

    public boolean isRunning(Long jobId) {
        Process p = running.get(jobId);
        return p != null && p.isAlive();
    }

    public void submit(GalleryJobDO job, List<String> command, Path logFile, FinishCallback callback) {
        Path dir = logFile.getParent();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("cannot create job dir " + dir, e);
        }
        GalleryJobDO waiting = new GalleryJobDO();
        waiting.setId(job.getId());
        waiting.setStatus(GalleryConstants.JOB_WAITING);
        waiting.setLogPath(logFile.toString());
        galleryJobMapper.updateById(waiting);
        pool.submit(() -> run(job.getId(), command, logFile, callback));
    }

    private void run(Long jobId, List<String> command, Path logFile, FinishCallback callback) {
        Process process = null;
        Map<String, Object> result = new LinkedHashMap<>();
        String error = null;
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            logWriter.write("# " + String.join(" ", command) + "\n");
            logWriter.flush();

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(settingService.workdir()));
            pb.redirectErrorStream(true);
            pb.environment().put("MINERU_TOKEN", settingService.get("mineru_api_token", ""));
            pb.environment().put("MINERU_BASE_URL", settingService.get("mineru_api_base", "https://mineru.net"));
            pb.environment().put("MINERU_MODEL", settingService.get("mineru_model", "pipeline"));
            process = pb.start();
            running.put(jobId, process);
            GalleryJobDO started = new GalleryJobDO();
            started.setId(jobId);
            started.setStatus(GalleryConstants.JOB_RUNNING);
            started.setPid(process.pid());
            started.setStartedTime(LocalDateTime.now());
            started.setProgress(0);
            galleryJobMapper.updateById(started);
            notifyJob(jobId);

            StringBuilder tail = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logWriter.write(line + "\n");
                    logWriter.flush();
                    if (tail.length() < 20000) {
                        tail.append(line).append('\n');
                    } else {
                        tail.setLength(0);
                    }
                    if (line.startsWith("@@PROGRESS ")) {
                        applyProgress(jobId, line.substring("@@PROGRESS ".length()));
                    } else if (line.startsWith("@@RESULT ")) {
                        Map<String, Object> parsed = JsonUtils.parseObjectQuietly(
                                line.substring("@@RESULT ".length()), Map.class);
                        if (parsed != null) {
                            result.putAll(parsed);
                        }
                    }
                }
            }
            tails.put(jobId, tail);
            int exit = process.waitFor();
            if (exit != 0 && result.get("error") == null) {
                error = "pipeline exited with code " + exit;
            }
            if (Boolean.FALSE.equals(result.get("ok")) && result.get("error") != null) {
                error = String.valueOf(result.get("error"));
            }
            finish(jobId, exit == 0 && error == null, result, error);
            callback.onFinish(galleryJobMapper.selectById(jobId), exit, result, error);
        } catch (Exception e) {
            log.error("[run][job {}] failed", jobId, e);
            error = e.getMessage();
            finish(jobId, false, result, error);
            try {
                callback.onFinish(galleryJobMapper.selectById(jobId), -1, result, error);
            } catch (Exception ignored) {
                // ignore
            }
        } finally {
            running.remove(jobId);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyProgress(Long jobId, String json) {
        Map<String, Object> payload = JsonUtils.parseObjectQuietly(json, Map.class);
        if (payload == null) {
            return;
        }
        Integer done = asInt(payload.get("done"));
        Integer total = asInt(payload.get("total"));
        Integer failed = asInt(payload.get("failed"));
        GalleryJobDO update = new GalleryJobDO();
        update.setId(jobId);
        update.setDone(done);
        update.setTotal(total);
        update.setFailed(failed);
        if (done != null && total != null && total > 0) {
            update.setProgress((int) Math.min(99, Math.round(done * 100.0 / total)));
        }
        galleryJobMapper.updateById(update);
        notifyJob(jobId);
    }

    private void notifyJob(Long jobId) {
        try {
            galleryJobNotifier.notifyJobChanged(galleryJobMapper.selectById(jobId));
        } catch (Exception ignored) {
            // SSE 推送失败不影响任务
        }
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private void finish(Long jobId, boolean ok, Map<String, Object> result, String error) {
        GalleryJobDO update = new GalleryJobDO();
        update.setId(jobId);
        update.setStatus(ok ? GalleryConstants.JOB_SUCCESS : GalleryConstants.JOB_FAILED);
        update.setProgress(ok ? 100 : null);
        update.setFinishedTime(LocalDateTime.now());
        update.setResultJson(JsonUtils.toJsonString(result));
        update.setErrorMsg(truncate(error, 900));
        galleryJobMapper.updateById(update);
        notifyJob(jobId);
    }

    public boolean cancel(Long jobId) {
        Process process = running.get(jobId);
        if (process == null || !process.isAlive()) {
            return false;
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        GalleryJobDO update = new GalleryJobDO();
        update.setId(jobId);
        update.setStatus(GalleryConstants.JOB_CANCELED);
        update.setFinishedTime(LocalDateTime.now());
        update.setErrorMsg("canceled by operator");
        galleryJobMapper.updateById(update);
        notifyJob(jobId);
        return true;
    }

    public String tail(Long jobId) {
        StringBuilder sb = tails.get(jobId);
        return sb == null ? "" : sb.toString();
    }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        running.forEach((id, p) -> out.add(id + ":" + p.pid() + ":" + p.isAlive()));
        return out;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    @PreDestroy
    public void shutdown() {
        running.values().forEach(p -> {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        });
        pool.shutdownNow();
    }
}
