package cn.iocoder.yudao.module.gallery.service.search;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryFilterOptionDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GalleryFilterOptionMapper;
import cn.iocoder.yudao.module.gallery.service.setting.GallerySettingService;
import cn.iocoder.yudao.module.gallery.service.site.GallerySiteFileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 把 gallery_figure 目录同步到 Meilisearch 的 figures 索引。
 *
 * 与后台参数联动：meili_url / meili_index / meili_api_key / meili_enabled，
 * 默认 http://127.0.0.1:7700 + figures，可在「管线参数」中覆盖。
 */
@Service
@Slf4j
public class GallerySearchIndexServiceImpl implements GallerySearchIndexService {

    private static final String DEFAULT_URL = "http://127.0.0.1:7700";
    private static final String DEFAULT_INDEX = "figures";
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,511}$");

    private static final List<String> SEARCHABLE = List.of(
            "title", "authors", "keywords", "venue", "pattern", "tier", "id");
    private static final List<String> FILTERABLE = List.of(
            "venue", "year", "tier", "pattern", "award");
    private static final List<String> SORTABLE = List.of("year", "title", "score");

    @Resource
    private GallerySettingService settingService;
    @Resource
    private GallerySiteFileService siteFileService;
    @Resource
    private GalleryFilterOptionMapper optionMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** 索引的 settings 只需成功下发一次；失败时下次发布会重试。 */
    private volatile boolean settingsReady = false;

    @Override
    public Map<String, Object> syncAll() {
        return upsert(siteFileService.catalogRecords());
    }

    /** 启动时对齐一次索引（可在参数里用 meili_sync_on_startup=false 关闭）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            if (!settingService.getBool("meili_sync_on_startup", true)) {
                log.info("[meili] 已关闭启动同步（meili_sync_on_startup=false）");
                return;
            }
            log.info("[meili] 启动同步检索索引…");
            syncAll();
        } catch (Exception e) {
            log.warn("[meili] 启动同步失败：{}", e.getMessage());
        }
    }

    @Override
    public synchronized Map<String, Object> upsert(List<Map<String, Object>> records) {
        Map<String, Object> result = new LinkedHashMap<>();
        String url = trimSlash(settingService.get("meili_url", DEFAULT_URL));
        String index = settingService.get("meili_index", DEFAULT_INDEX);
        String apiKey = settingService.get("meili_api_key", "");
        boolean enabled = settingService.getBool("meili_enabled", true);
        result.put("index", index);
        if (!enabled) {
            log.info("[meili] 已关闭（meili_enabled=false），跳过索引同步");
            result.put("ok", false);
            result.put("skipped", true);
            return result;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[meili] 未配置 meili_api_key，跳过索引同步");
            result.put("ok", false);
            result.put("error", "未配置 meili_api_key");
            return result;
        }

        long start = System.currentTimeMillis();
        try {
            ensureIndex(url, index, apiKey);
            ensureSettings(url, index, apiKey);
            List<Map<String, Object>> documents = toDocuments(records);
            log.info("[meili] 开始上传索引 {}：{} 条文档", index, documents.size());
            HttpResult submit = call(url, "/indexes/" + index + "/documents?primaryKey=id",
                    "POST", JsonUtils.toJsonString(documents), apiKey);
            requireSuccess(submit, "提交文档");
            String taskUid = taskUid(submit.body());
            log.info("[meili] 文档任务已提交：task={}，等待索引完成…", taskUid);
            Map<String, Object> task = waitTask(url, index, apiKey, taskUid);
            int indexed = indexedCount(task, documents.size());
            long cost = System.currentTimeMillis() - start;
            log.info("[meili] 索引同步成功：index={} task={} indexed={} 耗时={}ms",
                    index, taskUid, indexed, cost);
            result.put("ok", true);
            result.put("taskUid", taskUid);
            result.put("indexed", indexed);
            result.put("durationMs", cost);
        } catch (Exception e) {
            log.warn("[meili] 索引同步失败（不影响发布）：{}", e.getMessage());
            result.put("ok", false);
            result.put("error", String.valueOf(e.getMessage()));
        }
        return result;
    }

    // ------------------------------------------------------------------

    private void ensureIndex(String url, String index, String apiKey) throws Exception {
        // 先查询：已存在就不再提交创建任务（Meilisearch 对已存在索引会异步返回失败任务）
        HttpResult existing = call(url, "/indexes/" + index, "GET", null, apiKey);
        if (existing.status() == 200) {
            log.debug("[meili] 索引 {} 已存在", index);
            return;
        }
        if (existing.status() != 404) {
            requireSuccess(existing, "检查索引");
        }
        String body = JsonUtils.toJsonString(Map.of("uid", index, "primaryKey", "id"));
        HttpResult response = call(url, "/indexes", "POST", body, apiKey);
        if (response.status() == 201 || response.status() == 202) {
            String taskUid = taskUid(response.body());
            try {
                waitTask(url, index, apiKey, taskUid);
                log.info("[meili] 已创建索引 {}（task={}）", index, taskUid);
            } catch (IllegalStateException e) {
                // 并发创建时另一个请求可能已完成，索引存在即视为成功
                if (!String.valueOf(e.getMessage()).contains("already exists")) {
                    throw e;
                }
                log.debug("[meili] 索引 {} 已由其他请求创建", index);
            }
        } else {
            requireSuccess(response, "创建索引");
        }
    }

    private void ensureSettings(String url, String index, String apiKey) throws Exception {
        if (settingsReady) {
            return;
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("searchableAttributes", SEARCHABLE);
        settings.put("filterableAttributes", FILTERABLE);
        settings.put("sortableAttributes", SORTABLE);
        settings.put("pagination", Map.of("maxTotalHits", 4000));
        HttpResult response = call(url, "/indexes/" + index + "/settings",
                "PATCH", JsonUtils.toJsonString(settings), apiKey);
        requireSuccess(response, "更新索引设置");
        waitTask(url, index, apiKey, taskUid(response.body()));
        settingsReady = true;
        log.info("[meili] 索引 {} 的 settings 已更新", index);
    }

    private List<Map<String, Object>> toDocuments(List<Map<String, Object>> records) {
        Map<String, Map<String, String>> labels = optionLabels();
        List<Map<String, Object>> documents = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            Map<String, Object> document = new LinkedHashMap<>(record);
            String id = String.valueOf(record.get("id"));
            if (!SAFE_ID.matcher(id).matches()) {
                document.put("figureId", id);
                document.put("id", id.replaceAll("[^A-Za-z0-9_-]", "_"));
            }
            StringBuilder keywords = new StringBuilder();
            String venue = text(record.get("venue"));
            String pattern = text(record.get("pattern"));
            String tier = text(record.get("tier"));
            append(keywords, label(labels, "venue", venue), venue);
            append(keywords, label(labels, "pattern", pattern), pattern);
            append(keywords, label(labels, "tier", tier), tier);
            if (record.get("award") != null) {
                append(keywords, "best paper award outstanding 最佳论文 荣誉提名");
            }
            append(keywords, text(record.get("year")));
            document.put("keywords", keywords.toString().trim());
            documents.add(document);
        }
        return documents;
    }

    private Map<String, Map<String, String>> optionLabels() {
        Map<String, Map<String, String>> labels = new HashMap<>();
        for (GalleryFilterOptionDO option : optionMapper.selectListOrdered()) {
            String zh = text(option.getLabelZh());
            String en = text(option.getLabelEn());
            String joined = (zh + " " + en).trim();
            labels.computeIfAbsent(option.getDimension(), key -> new HashMap<>())
                    .put(option.getOptionValue(), joined.isEmpty() ? option.getOptionValue() : joined);
        }
        return labels;
    }

    private static String label(Map<String, Map<String, String>> labels, String dimension, String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        Map<String, String> values = labels.get(dimension);
        return values == null ? "" : values.getOrDefault(value, "");
    }

    private static void append(StringBuilder builder, String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.append(value).append(' ');
            }
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ------------------------------------------------------------------

    private Map<String, Object> waitTask(String url, String index, String apiKey, String taskUid)
            throws Exception {
        for (int attempt = 0; attempt < 150; attempt++) {
            HttpResult response = call(url, "/tasks/" + taskUid, "GET", null, apiKey);
            requireSuccess(response, "查询索引任务");
            Map<String, Object> task = parseMap(response.body());
            String status = String.valueOf(task.get("status"));
            if ("succeeded".equals(status)) {
                return task;
            }
            if ("failed".equals(status) || "canceled".equals(status)) {
                throw new IllegalStateException("索引任务 " + taskUid + " " + status + "：" + task.get("error"));
            }
            Thread.sleep(200L);
        }
        throw new IllegalStateException("等待索引任务 " + taskUid + " 超时");
    }

    private int indexedCount(Map<String, Object> task, int fallback) {
        Object details = task.get("details");
        if (details instanceof Map<?, ?> map) {
            Object indexed = map.get("indexedDocuments");
            if (indexed instanceof Number number) {
                return number.intValue();
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(String body) {
        return JsonUtils.parseObject(body, Map.class);
    }

    private String taskUid(String body) {
        Object uid = parseMap(body).get("taskUid");
        if (uid == null) {
            throw new IllegalStateException("Meilisearch 响应缺少 taskUid：" + body);
        }
        return String.valueOf(uid);
    }

    private HttpResult call(String baseUrl, String path, String method, String body, String apiKey)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new HttpResult(response.statusCode(), response.body());
    }

    private static void requireSuccess(HttpResult response, String action) {
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException(action + "失败（HTTP " + response.status() + "）："
                    + abbreviate(response.body()));
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "…";
    }

    private static String trimSlash(String url) {
        String value = url == null || url.isBlank() ? DEFAULT_URL : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record HttpResult(int status, String body) {
    }
}
