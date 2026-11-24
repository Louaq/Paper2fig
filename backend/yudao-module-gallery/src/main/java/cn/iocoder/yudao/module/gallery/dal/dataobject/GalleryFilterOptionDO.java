package cn.iocoder.yudao.module.gallery.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 静态画廊筛选项配置：会议、年份、等级、模式。
 */
@TableName("gallery_filter_option")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryFilterOptionDO extends BaseDO {

    @TableId
    private Long id;
    /** 维度：venue / year / tier / pattern */
    private String dimension;
    /** 选项值 */
    private String optionValue;
    /** 中文显示名 */
    private String labelZh;
    /** 英文显示名 */
    private String labelEn;
    /** 排序（升序） */
    private Integer sort;
}
