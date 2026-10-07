package com.easyoa.file.dto;

import java.time.Instant;

import com.easyoa.file.domain.FileObject;

/**
 * 附件视图（元数据 + 统一下载入口）。
 *
 * <p>附件不作为公开静态资源：{@link #downloadUrl} 指向 {@code GET /api/files/{id}}，
 * 由后端完成认证 → 资源权限 → 文件权限校验后流式下载。
 */
public record FileView(
        Long id,
        String originalName,
        String mimeType,
        long size,
        String sha256,
        Long uploaderId,
        String uploaderName,
        String resourceType,
        Long resourceId,
        String downloadUrl,
        Instant createdAt) {

    public static FileView from(FileObject file) {
        return new FileView(
                file.getId(),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSize(),
                file.getSha256(),
                file.getUploader().getId(),
                file.getUploader().getDisplayName(),
                file.getResourceType().name(),
                file.getResourceId(),
                "/api/files/" + file.getId(),
                file.getCreatedAt());
    }
}