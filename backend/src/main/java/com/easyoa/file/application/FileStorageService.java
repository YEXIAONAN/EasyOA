package com.easyoa.file.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;

import jakarta.annotation.PostConstruct;

/**
 * 附件磁盘存储。
 *
 * <p>安全要求（产品规范「文件安全」）：
 * <ul>
 *   <li>磁盘文件名使用 UUID 随机名，用户原始文件名绝不参与真实路径；</li>
 *   <li>目录按存储名前两位分片，避免单目录文件过多；</li>
 *   <li>读取时校验存储名格式并强制 {@code normalize} + 前缀检查，防止目录遍历；</li>
 *   <li>附件不注册为静态资源，只能通过 {@code GET /api/files/{fileId}} 下载。</li>
 * </ul>
 */
@Service
public class FileStorageService {

    /** 存储名：32 位十六进制（UUID 去横线）+ 可选的安全扩展名。 */
    private static final Pattern STORED_NAME_PATTERN = Pattern.compile("^[0-9a-f]{32}(\\.[a-z0-9]{1,10})?$");

    private static final int BUFFER_SIZE = 8192;

    private final Path root;

    public FileStorageService(@Value("${easyoa.storage.path}") String storagePath) {
        this.root = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建附件存储目录：" + root, ex);
        }
    }

    /** 落盘结果：存储名 / 实际字节数 / SHA-256。 */
    public record StoredFile(String storedName, long size, String sha256) {
    }

    /** 流式写入磁盘并计算 SHA-256（上传入口使用）。 */
    public StoredFile store(InputStream input, String extension) {
        String storedName = UUID.randomUUID().toString().replace("-", "")
                + (extension.isEmpty() ? "" : "." + extension);
        Path target = resolve(storedName);
        MessageDigest digest = newDigest();
        long size = 0;
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = input; OutputStream out = Files.newOutputStream(target,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    size += read;
                }
            }
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "附件写入失败，请稍后重试");
        }
        return new StoredFile(storedName, size, HexFormat.of().formatHex(digest.digest()));
    }

    /** 按存储名读取（下载入口使用）。 */
    public Resource load(String storedName) {
        Path path = resolve(storedName);
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("文件不存在");
        }
        return new FileSystemResource(path);
    }

    private Path resolve(String storedName) {
        if (storedName == null || !STORED_NAME_PATTERN.matcher(storedName).matches()) {
            // 存储名必须是系统生成的格式，杜绝任何用户可控路径
            throw ApiException.notFound("文件不存在");
        }
        Path path = root.resolve(storedName.substring(0, 2)).resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.notFound("文件不存在");
        }
        return path;
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256 实现", ex);
        }
    }
}