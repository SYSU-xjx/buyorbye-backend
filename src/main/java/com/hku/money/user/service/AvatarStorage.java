package com.hku.money.user.service;

import com.hku.money.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AvatarStorage {
  public static final long MAX_BYTES = 5L * 1024 * 1024;
  private final Path root;

  public AvatarStorage(@Value("${app.avatar.storage-dir}") String directory) {
    root = Path.of(directory).toAbsolutePath().normalize();
  }

  public record Stored(Path path, String filename) {}

  public Stored save(MultipartFile file) {
    if (file == null || file.isEmpty()) throw ApiException.bad("请选择头像图片");
    if (file.getSize() > MAX_BYTES) throw new ApiException(413, "FILE_TOO_LARGE", "头像图片不得超过5MB");
    BufferedImage image;
    String format;
    try (InputStream input = file.getInputStream()) {
      byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new ApiException(413, "FILE_TOO_LARGE", "头像图片不得超过5MB");
      try (ImageInputStream images =
          ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        if (images == null) throw ApiException.bad("图片内容无效");
        var readers = ImageIO.getImageReaders(images);
        if (!readers.hasNext()) throw ApiException.bad("图片内容无效");
        var reader = readers.next();
        try {
          format = reader.getFormatName().toLowerCase(Locale.ROOT);
          if (!Set.of("png", "jpeg", "jpg").contains(format))
            throw new ApiException(415, "IMAGE_TYPE_UNSUPPORTED", "仅支持PNG和JPEG图片");
          reader.setInput(images, true, true);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || width > 4096 || height > 4096)
            throw ApiException.bad("头像图片尺寸不得超过4096×4096");
          image = reader.read(0);
          if (image == null) throw ApiException.bad("图片内容无效");
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException e) {
      throw ApiException.bad("图片内容无法读取");
    }
    String extension = format.equals("png") ? "png" : "jpg";
    String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
    Path target = root.resolve(filename);
    try {
      Files.createDirectories(root);
      // Re-encode decoded pixels, stripping uploaded metadata and any appended payload.
      try (OutputStream output =
          Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        if (!ImageIO.write(image, extension.equals("jpg") ? "jpeg" : "png", output))
          throw new IOException("No image encoder available");
      }
      return new Stored(target, filename);
    } catch (IOException e) {
      discard(target);
      throw new ApiException(500, "AVATAR_STORAGE_FAILED", "头像保存失败");
    } finally {
      image.flush();
    }
  }

  public Path find(String filename) {
    if (!filename.matches("[0-9a-f]{32}\\.(png|jpg)"))
      throw new ApiException(404, "AVATAR_NOT_FOUND", "头像不存在");
    Path path = root.resolve(filename).normalize();
    if (!path.getParent().equals(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      throw new ApiException(404, "AVATAR_NOT_FOUND", "头像不存在");
    return path;
  }

  public void discard(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      org.slf4j.LoggerFactory.getLogger(AvatarStorage.class)
          .warn("Could not remove unused avatar", e);
    }
  }
}
