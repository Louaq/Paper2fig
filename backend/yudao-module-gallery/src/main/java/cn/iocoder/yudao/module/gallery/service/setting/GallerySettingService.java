package cn.iocoder.yudao.module.gallery.service.setting;

import cn.iocoder.yudao.module.gallery.dal.dataobject.GallerySettingDO;

import java.util.List;
import java.util.Map;

public interface GallerySettingService {

    /** 读取参数，不存在时返回默认值 */
    String get(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    boolean getBool(String key, boolean defaultValue);

    Map<String, String> getAll();

    List<GallerySettingDO> getList();

    void update(Map<String, String> values);

    /** python 可执行文件 */
    String python();

    /** 管线工作目录 */
    String workdir();

    /** 静态站根目录 */
    String siteDir();
}
