package cn.iocoder.yudao.module.gallery.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 离线管线任务：采集 / 下载 / 裁剪 / 发布。
 */
@TableName("gallery_job")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryJobDO extends BaseDO {

    @TableId
    private Long id;
    /** CRAWL/FETCH/CROP/PUBLISH */
    private String kind;
    private String venue;
    private Integer year;
    /** 0 等待 1 运行 2 成功 3 失败 4 取消 */
    private Integer status;
    private Integer progress;
    private Integer total;
    private Integer done;
    private Integer failed;
    private String paramsJson;
    private String resultJson;
    private String logPath;
    private Long pid;
    private String errorMsg;
    private java.time.LocalDateTime startedTime;
    private java.time.LocalDateTime finishedTime;
    /** 幂等键：同一来源同一年份同一时刻只允许一个进行中的任务 */
    private String dedupeKey;
}
