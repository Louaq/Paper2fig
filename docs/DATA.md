# 数据与图片

## 图片目录

```text
site/images/<venue>/final/<figure-id>.jpg
site/images/<venue>/thumb/<figure-id>.webp
```

- `final/`：灯箱和「单独查看图片」使用，建议宽度 800–1400px 的 JPEG。
- `thumb/`：列表卡片使用，建议宽 720px 的 WebP。
- 前端由 `final` 路径推导缩略图：`/final/` → `/thumb/`，`.jpg` → `.webp`。
- 支持的 venue：`aaai acl cvpr eccv iclr icme icml ijcai miccai mm neurips`。

本仓库的 `site/images/` 只有空目录和说明文件，**不含任何图片**。

## 元数据

前端读取 `site/assets/figures.js`：

```js
window.FIGURES = [
  {
    id: "cvpr2025-1234",
    venue: "cvpr",
    year: 2025,
    title: "Paper title",
    authors: ["A", "B"],
    pattern: "framework",
    tier: "oral",
    award: null,
    image: "images/cvpr/final/cvpr2025-1234.jpg",
    paper: "https://...",
    w: 1200,
    h: 600
  }
];
```

后台发布或图片管理会从 `gallery_figure` 表重新生成该文件和 `sitemap.xml`。

`site/data/site-config.js` / `site/data/site-config.json` 控制首页筛选维度
（会议 / 年份 / 等级 / 图类型），可在后台「画廊管理 → 前端配置」维护。

## 导入已有数据

1. 启动后台，进入「画廊管理 → 从静态站同步」，从 `site/data/figures.json` 导入；
2. 或直接使用「图片管理」新增/编辑图片；
3. 或运行采集管线：

```bash
cd pipeline
.venv/bin/python -m pipeline.cli collect --venue cvpr --year 2026 --limit 50 --out papers.jsonl
.venv/bin/python -m pipeline.cli crop --input items.json --out results.jsonl --dpi 200
.venv/bin/python -m pipeline.cli publish --input export.json --site /opt/1panel/www/sites/louaq.net/index --apply
```

> 后台发布时会清理已发布的 PDF 和裁剪中间文件；`site/images/` 与 `backups-data` 不受影响。

## 备份

| 数据 | Volume |
|---|---|
| 数据库 | `mysql-data` |
| 静态站 / 图片 | `site-data` |
| 上传文件 | `files-data` |
| 发布备份 | `backups-data` |
| 管线缓存 | `pipeline-workspace` |

最简单的方式是停止写入后打包对应 volume，或使用 `docker run --rm -v <volume>:/data -v $PWD:/backup alpine tar czf /backup/x.tgz -C /data .`。
