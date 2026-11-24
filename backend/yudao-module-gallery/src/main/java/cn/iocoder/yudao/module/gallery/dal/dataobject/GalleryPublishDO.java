package cn.iocoder.yudao.module.gallery.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 一次发布记录（预检或实际发布），记录数量与备份位置。
 */
@TableName("gallery_publish")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryPublishDO extends BaseDO {

    @TableId
    private Long id;
    private String version;
    private String siteDir;
    private Integer added;
    private Integer updated;
    private Integer total;
    /** 0 预检 1 成功 2 失败 */
    private Integer status;
    private String backupDir;
    private String resultJson;
    private Long jobId;
    private String operatorName;
}
