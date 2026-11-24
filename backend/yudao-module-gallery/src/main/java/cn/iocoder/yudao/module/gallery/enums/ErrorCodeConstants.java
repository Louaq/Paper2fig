package cn.iocoder.yudao.module.gallery.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 画廊模块错误码，模块区间 1_030_000_000。
 */
public interface ErrorCodeConstants {

    ErrorCode PAPER_NOT_EXISTS = new ErrorCode(1_030_000_001, "论文不存在");
    ErrorCode CROP_NOT_EXISTS = new ErrorCode(1_030_000_002, "裁剪记录不存在");
    ErrorCode CROP_NOT_PENDING = new ErrorCode(1_030_000_003, "该裁剪记录已审核");
    ErrorCode JOB_NOT_EXISTS = new ErrorCode(1_030_000_004, "任务不存在");
    ErrorCode JOB_NOT_RUNNING = new ErrorCode(1_030_000_005, "任务不在运行中");
    ErrorCode JOB_CONFLICT = new ErrorCode(1_030_000_006, "同一来源同一年份已有运行中的任务");
    ErrorCode PIPELINE_FAILED = new ErrorCode(1_030_000_007, "离线管线执行失败");
    ErrorCode PIPELINE_NOT_CONFIGURED = new ErrorCode(1_030_000_008, "管线未配置（python/workdir）");
    ErrorCode NO_APPROVED_CROP = new ErrorCode(1_030_000_009, "没有待发布的审核通过记录");
    ErrorCode PUBLISH_FAILED = new ErrorCode(1_030_000_010, "发布失败");
    ErrorCode VENUE_INVALID = new ErrorCode(1_030_000_011, "不支持的会议");
    ErrorCode FIGURE_NOT_EXISTS = new ErrorCode(1_030_000_012, "图片不存在");
    ErrorCode FIGURE_UPDATE_FAILED = new ErrorCode(1_030_000_013, "图片属性保存失败");
    ErrorCode OPTION_NOT_EXISTS = new ErrorCode(1_030_000_014, "筛选项不存在");
    ErrorCode OPTION_EXISTS = new ErrorCode(1_030_000_015, "同维度下已存在该选项");
    ErrorCode OPTION_INVALID = new ErrorCode(1_030_000_016, "筛选项不合法（维度或值格式错误）");
    ErrorCode CROP_DELETE_FAILED = new ErrorCode(1_030_000_017, "删除裁剪图片失败");
    ErrorCode JOB_RUNNING_CANNOT_DELETE = new ErrorCode(1_030_000_018, "任务正在运行，请先取消后再删除");
    ErrorCode PUBLISH_NOT_EXISTS = new ErrorCode(1_030_000_019, "发布记录不存在");
    ErrorCode MINERU_NOT_CONFIGURED = new ErrorCode(1_030_000_020, "MinerU 在线解析未配置 API Token");
    ErrorCode PAPER_PDF_INVALID = new ErrorCode(1_030_000_021, "请上传有效的 PDF 文件（不超过 100MB）");
    ErrorCode PAPER_PDF_SAVE_FAILED = new ErrorCode(1_030_000_022, "保存 PDF 文件失败");
}
