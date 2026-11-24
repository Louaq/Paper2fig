# Figure images are not included

This directory is intentionally empty in the open-source distribution.
The production gallery contains ~3,477 images (about 450 MB), which are not
part of the source repository.

Expected layout:

```
images/<venue>/final/<figure-id>.jpg    # full-size JPEG, used by the lightbox
images/<venue>/thumb/<figure-id>.webp   # 720px WebP, used by the gallery cards
```

Supported venue directories: aaai, acl, cvpr, eccv, iclr, icme, icml,
ijcai, miccai, mm, neurips.

You can populate this directory by:

1. Using the admin UI: 画廊管理 → 图片管理 → 新增 / 替换图片; or
2. Running the Python pipeline to collect papers, crop Figure 1, then publish; or
3. Copying an existing `images/` tree into this directory.

Keep the same directory layout so `assets/figures.js` can derive the thumbnail
path by replacing `/final/` with `/thumb/` and `.jpg` with `.webp`.
