package cn.iocoder.yudao.module.gallery.controller.admin.job;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobCreateReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobPageReqVO;
import cn.iocoder.yudao.module.gallery.controller.admin.job.vo.JobRespVO;
import cn.iocoder.yudao.module.gallery.dal.dataobject.GalleryJobDO;
import cn.iocoder.yudao.module.gallery.service.job.GalleryJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 画廊管线任务")
@RestController
@RequestMapping("/gallery/job")
@Validated
public class GalleryJobController {

    @Resource
    private GalleryJobService jobService;

    @GetMapping("/page")
    @Operation(summary = "获得任务分页")
    @PreAuthorize("@ss.hasPermission('gallery:job:query')")
    public CommonResult<PageResult<JobRespVO>> getJobPage(@Valid JobPageReqVO reqVO) {
        PageResult<GalleryJobDO> page = jobService.getJobPage(reqVO);
        PageResult<JobRespVO> result = BeanUtils.toBean(page, JobRespVO.class);
        if (result.getList() != null) {
            result.getList().forEach(vo -> {
                if (vo.getStartedTime() != null) {
                    var end = vo.getFinishedTime() == null ? java.time.LocalDateTime.now() : vo.getFinishedTime();
                    vo.setDurationSeconds(Duration.between(vo.getStartedTime(), end).getSeconds());
                }
            });
        }
        return success(result);
    }

    @GetMapping("/get")
    @Operation(summary = "获得任务详情")
    @PreAuthorize("@ss.hasPermission('gallery:job:query')")
    public CommonResult<JobRespVO> getJob(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(jobService.getJob(id), JobRespVO.class));
    }

    @PostMapping("/create-crawl")
    @Operation(summary = "创建采集任务（定时发现新论文）")
    @PreAuthorize("@ss.hasPermission('gallery:job:create')")
    public CommonResult<Long> createCrawl(@Valid @RequestBody JobCreateReqVO reqVO) {
        return success(jobService.createCrawlJob(reqVO));
    }

    @PostMapping("/create-crop")
    @Operation(summary = "创建裁剪任务")
    @PreAuthorize("@ss.hasPermission('gallery:job:create')")
    public CommonResult<Long> createCrop(@Valid @RequestBody JobCreateReqVO reqVO) {
        return success(jobService.createCropJob(reqVO));
    }

    @PostMapping("/create-fetch")
    @Operation(summary = "创建 PDF 下载任务")
    @PreAuthorize("@ss.hasPermission('gallery:job:create')")
    public CommonResult<Long> createFetch(@Valid @RequestBody JobCreateReqVO reqVO) {
        return success(jobService.createFetchJob(reqVO));
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消任务")
    @PreAuthorize("@ss.hasPermission('gallery:job:cancel')")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        jobService.cancelJob(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务记录")
    @PreAuthorize("@ss.hasPermission('gallery:job:cancel')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        jobService.deleteJob(id);
        return success(true);
    }

    @GetMapping("/log")
    @Operation(summary = "查看任务日志（末尾 N 行）")
    @PreAuthorize("@ss.hasPermission('gallery:job:query')")
    public CommonResult<Map<String, Object>> log(@RequestParam("id") Long id,
                                                 @RequestParam(value = "lines", defaultValue = "200") Integer lines) {
        return success(Map.of("log", jobService.getLog(id, lines)));
    }
}
