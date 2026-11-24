package cn.iocoder.yudao.module.gallery.service.setting;

import cn.iocoder.yudao.module.gallery.dal.dataobject.GallerySettingDO;
import cn.iocoder.yudao.module.gallery.dal.mysql.GallerySettingMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GallerySettingServiceImpl implements GallerySettingService {

    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("python", "/opt/apps/gallery-pipeline/.venv/bin/python");
        DEFAULTS.put("workdir", "/opt/apps/gallery-pipeline");
        DEFAULTS.put("site_dir", "/opt/1panel/www/sites/louaq.net/index");
        DEFAULTS.put("dpi", "200");
        DEFAULTS.put("max_pages", "6");
        DEFAULTS.put("include_caption", "false");
        DEFAULTS.put("dup_hamming", "6");
        DEFAULTS.put("min_confidence_auto", "0.85");
        DEFAULTS.put("publish_update_sitemap", "true");
        DEFAULTS.put("crawl_cron_hint", "0 30 3 * * ?");
        DEFAULTS.put("backup_dir", "/opt/backups/fig-gallery");
        DEFAULTS.put("backup_keep_days", "30");
        DEFAULTS.put("backup_keep_min", "20");
        DEFAULTS.put("backup_keep_max", "200");
        DEFAULTS.put("backup_archive_days", "7");
        DEFAULTS.put("mineru_api_base", "https://mineru.net");
        DEFAULTS.put("mineru_api_token", "");
        DEFAULTS.put("mineru_model", "pipeline");
        DEFAULTS.put("mineru_batch_size", "50");
        DEFAULTS.put("meili_url", "http://127.0.0.1:7700");
        DEFAULTS.put("meili_index", "figures");
        DEFAULTS.put("meili_api_key", "");
        DEFAULTS.put("meili_enabled", "true");
        DEFAULTS.put("meili_sync_on_startup", "true");
    }

    @Resource
    private GallerySettingMapper settingMapper;

    @Override
    public String get(String key, String defaultValue) {
        GallerySettingDO row = settingMapper.selectByKey(key);
        if (row == null || row.getConfigValue() == null) {
            return DEFAULTS.getOrDefault(key, defaultValue);
        }
        return row.getConfigValue();
    }

    @Override
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    @Override
    public Map<String, String> getAll() {
        Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
        for (GallerySettingDO row : getList()) {
            values.put(row.getConfigKey(), row.getConfigValue());
        }
        return values;
    }

    @Override
    public List<GallerySettingDO> getList() {
        return settingMapper.selectList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Map<String, String> values) {
        values.forEach((key, value) -> {
            GallerySettingDO exists = settingMapper.selectByKey(key);
            if (exists == null) {
                settingMapper.insert(GallerySettingDO.builder()
                        .configKey(key).configValue(value).remark(DEFAULTS.containsKey(key) ? "内置参数" : null).build());
            } else {
                exists.setConfigValue(value);
                settingMapper.updateById(exists);
            }
        });
    }

    @Override
    public String python() {
        return get("python", DEFAULTS.get("python"));
    }

    @Override
    public String workdir() {
        return get("workdir", DEFAULTS.get("workdir"));
    }

    @Override
    public String siteDir() {
        return get("site_dir", DEFAULTS.get("site_dir"));
    }
}
