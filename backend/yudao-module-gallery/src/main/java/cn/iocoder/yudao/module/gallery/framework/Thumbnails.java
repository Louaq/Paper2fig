package cn.iocoder.yudao.module.gallery.framework;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.Base64;

/**
 * 生成审核用的 base64 缩略图。前端 img 标签无法携带 Authorization 头，
 * 所以直接把小图塞进接口响应，避免额外开一个匿名图片接口。
 */
public final class Thumbnails {

    private Thumbnails() {
    }

    public static String dataUri(String path, int maxWidth) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File file = Path.of(path).toFile();
        if (!file.isFile()) {
            return null;
        }
        try {
            BufferedImage src = ImageIO.read(file);
            if (src == null) {
                return null;
            }
            int width = Math.min(maxWidth, src.getWidth());
            int height = Math.max(1, (int) Math.round(src.getHeight() * (width / (double) src.getWidth())));
            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(src, 0, 0, width, height, null);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "jpg", baos);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
