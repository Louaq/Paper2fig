package cn.iocoder.yudao.module.gallery.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 管线参数（python 路径、站点目录、DPI、阈值…）。
 */
@TableName("gallery_setting")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GallerySettingDO extends BaseDO {

    @TableId
    private Long id;
    private String configKey;
    private String configValue;
    private String remark;
}
