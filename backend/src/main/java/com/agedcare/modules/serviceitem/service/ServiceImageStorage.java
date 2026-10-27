package com.agedcare.modules.serviceitem.service;

import com.agedcare.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceImageStorage {
    private final Path directory;

    public ServiceImageStorage(@Value("${app.service-images-dir:./uploads/service-images}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String resourceLocation() {
        String uri = directory.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException("请选择不超过2MB的JPG或PNG图片");
        }
        // Verify the actual image decoder, dimensions and content, not the filename or MIME header.
        try (var stream = file.getInputStream(); var input = ImageIO.createImageInputStream(stream)) {
            if (input == null) throw new BusinessException("图片内容无效");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException("仅支持真实JPG或PNG图片");
            ImageReader reader = readers.next();
            try {
                if (!Set.of("jpeg", "png").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) {
                    throw new BusinessException("仅支持JPG或PNG图片");
                }
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 12_000_000) {
                    throw new BusinessException("图片尺寸不能超过1200万像素");
                }
                BufferedImage source = reader.read(0);
                double scale = Math.min(1.0, 1600.0 / Math.max(width, height));
                BufferedImage output = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = output.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null);
                } finally { graphics.dispose(); }
                Files.createDirectories(directory);
                String filename = UUID.randomUUID() + ".jpg";
                // Re-encode to strip arbitrary attachments and metadata; never trust an uploaded path.
                if (!ImageIO.write(output, "jpeg", directory.resolve(filename).toFile())) {
                    throw new IOException("JPEG writer unavailable");
                }
                return "/media/service-images/" + filename;
            } finally { reader.dispose(); }
        } catch (IOException e) {
            throw new BusinessException("图片读取或保存失败，请重试");
        }
    }
}
