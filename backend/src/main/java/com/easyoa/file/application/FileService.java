package com.easyoa.file.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.comment.domain.Comment;
import com.easyoa.comment.repository.CommentRepository;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.file.domain.FileObject;
import com.easyoa.file.domain.FileResourceType;
import com.easyoa.file.dto.FileView;
import com.easyoa.file.repository.FileObjectRepository;
import com.easyoa.project.application.ProjectPermissionService;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.task.application.TaskPermissionService;
import com.easyoa.task.domain.Task;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;

/**
 * 附件服务：上传（策略校验）、列表、下载（权限链 + 敏感审计）与软删除。
 *
 * <p>下载链路严格遵循产品规范：认证 → 资源权限（任务可见 / 评论所属任务可见）→
 * 文件权限 → 下载；非上传者下载写入 {@code FILE_DOWNLOADED_SENSITIVE} 审计。
 */
@Service
public class FileService {

    /** 危险文件策略：可执行 / 脚本类扩展名一律拒绝（v0.1.0 黑名单策略）。 */
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "com", "bat", "cmd", "msi", "scr", "pif", "cpl", "jar", "sh", "bash", "zsh",
            "ps1", "vbs", "wsf", "hta", "lnk", "dll", "so", "dylib", "app", "apk", "deb", "rpm", "bin", "run");

    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private final FileObjectRepository fileObjectRepository;
    private final FileStorageService storageService;
    private final CommentRepository commentRepository;
    private final TaskPermissionService taskPermissionService;
    private final ProjectPermissionService projectPermissionService;
    private final UserService userService;
    private final AuditService auditService;

    private final long maxFileSize;

    public FileService(FileObjectRepository fileObjectRepository, FileStorageService storageService,
            CommentRepository commentRepository, TaskPermissionService taskPermissionService,
            ProjectPermissionService projectPermissionService, UserService userService, AuditService auditService,
            @Value("${easyoa.storage.max-file-size:20971520}") long maxFileSize) {
        this.fileObjectRepository = fileObjectRepository;
        this.storageService = storageService;
        this.commentRepository = commentRepository;
        this.taskPermissionService = taskPermissionService;
        this.projectPermissionService = projectPermissionService;
        this.userService = userService;
        this.auditService = auditService;
        this.maxFileSize = maxFileSize;
    }

    // --- 上传 -------------------------------------------------------------------

    /** 上传任务附件（项目成员；策略校验：大小 / 扩展名 / 文件名清洗）。 */
    @Transactional
    public FileView uploadToTask(Long taskId, MultipartFile multipartFile) {
        SecurityUser actor = taskPermissionService.requireAuthenticated();
        Task task = taskPermissionService.requireViewable(taskId);
        requireParticipant(task);

        ValidatedUpload upload = validate(multipartFile);
        FileStorageService.StoredFile stored;
        try {
            stored = storageService.store(multipartFile.getInputStream(), upload.extension());
        } catch (java.io.IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "附件读取失败，请稍后重试");
        }
        User uploader = userService.getById(actor.id());
        FileObject file = new FileObject(upload.originalName(), stored.storedName(), upload.mimeType(),
                stored.size(), stored.sha256(), uploader, FileResourceType.TASK, taskId);
        fileObjectRepository.save(file);

        auditService.record(AuditEntry.action(AuditActions.FILE_UPLOADED, RiskLevel.NORMAL)
                .resource("FILE", file.getId())
                .after(Map.of("taskId", taskId, "name", file.getOriginalName(), "size", file.getSize()))
                .reason("上传任务附件"));
        return FileView.from(file);
    }

    // --- 查询 -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FileView> listTaskFiles(Long taskId) {
        taskPermissionService.requireViewable(taskId);
        return fileObjectRepository.findActiveByResource(FileResourceType.TASK, taskId).stream()
                .map(FileView::from)
                .toList();
    }

    /** 评论附件（供评论视图组装，按 commentId 分组）。 */
    @Transactional(readOnly = true)
    public Map<Long, List<FileView>> listByComments(Collection<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<FileView>> grouped = new LinkedHashMap<>();
        for (FileObject file : fileObjectRepository.findActiveByResources(FileResourceType.COMMENT, commentIds)) {
            grouped.computeIfAbsent(file.getResourceId(), key -> new ArrayList<>()).add(FileView.from(file));
        }
        return grouped;
    }

    // --- 下载 -------------------------------------------------------------------

    /** 下载结果：元数据 + 可流式输出的资源。 */
    public record DownloadFile(FileObject file, Resource resource) {
    }

    @Transactional
    public DownloadFile download(Long fileId) {
        SecurityUser actor = taskPermissionService.requireAuthenticated();
        FileObject file = requireActive(fileId);
        requireFileViewable(file);

        if (!file.getUploader().getId().equals(actor.id())) {
            // 敏感下载（非上传者本人）写入审计
            auditService.record(AuditEntry.action(AuditActions.FILE_DOWNLOADED_SENSITIVE, RiskLevel.ELEVATED)
                    .resource("FILE", file.getId())
                    .after(Map.of("name", file.getOriginalName(), "size", file.getSize(),
                            "resourceType", file.getResourceType().name(), "resourceId", file.getResourceId()))
                    .reason("下载他人上传的附件"));
        }
        return new DownloadFile(file, storageService.load(file.getStoredName()));
    }

    // --- 删除（软删除） -----------------------------------------------------------

    @Transactional
    public void delete(Long fileId) {
        SecurityUser actor = taskPermissionService.requireAuthenticated();
        FileObject file = requireActive(fileId);
        Task task = resolveTask(file);

        boolean uploader = file.getUploader().getId().equals(actor.id());
        if (!uploader) {
            ProjectRole role = projectPermissionService.roleOf(task.getProject().getId(), actor.id());
            if (!taskPermissionService.canManage(task, actor, role)) {
                throw ApiException.forbidden("只有上传者或任务负责人可以删除附件");
            }
        }
        file.markDeleted();
        fileObjectRepository.save(file);

        auditService.record(AuditEntry.action(AuditActions.FILE_DELETED, RiskLevel.ELEVATED)
                .resource("FILE", file.getId())
                .before(Map.of("name", file.getOriginalName(), "size", file.getSize()))
                .reason("删除附件（软删除，磁盘文件与记录保留）"));
    }

    // --- 评论附件挂载 ---------------------------------------------------------------

    /**
     * 评论发布时把「本人上传到同一任务的附件」迁移为评论附件。
     *
     * <p>先上传到任务、发布评论时挂载，避免评论创建与文件上传的时序耦合。
     */
    @Transactional
    public List<FileObject> linkToComment(Task task, Comment comment, List<Long> fileIds, SecurityUser actor) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<FileObject> linked = new ArrayList<>();
        for (Long fileId : new LinkedHashSet<>(fileIds)) {
            if (fileId == null) {
                continue;
            }
            FileObject file = requireActive(fileId);
            if (file.getResourceType() != FileResourceType.TASK
                    || !file.getResourceId().equals(task.getId())) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "附件不属于该任务，无法作为评论附件");
            }
            if (!file.getUploader().getId().equals(actor.id())) {
                throw ApiException.forbidden("只能引用自己上传的附件");
            }
            file.relink(FileResourceType.COMMENT, comment.getId());
            fileObjectRepository.save(file);
            linked.add(file);
        }
        return linked;
    }

    // --- 内部方法 ---------------------------------------------------------------

    private FileObject requireActive(Long fileId) {
        FileObject file = fileObjectRepository.findByIdWithUploader(fileId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> ApiException.notFound("文件不存在或无权访问"));
        return file;
    }

    /** 资源权限：TASK → 任务可见；COMMENT → 评论所属任务可见（非成员一律 404）。 */
    private void requireFileViewable(FileObject file) {
        try {
            if (file.getResourceType() == FileResourceType.TASK) {
                taskPermissionService.requireViewable(file.getResourceId());
                return;
            }
            Comment comment = commentRepository.findById(file.getResourceId())
                    .orElseThrow(() -> ApiException.notFound("文件不存在或无权访问"));
            taskPermissionService.requireViewable(comment.getTask().getId());
        } catch (ApiException ex) {
            if (ex.errorCode() == ErrorCode.NOT_FOUND) {
                throw ApiException.notFound("文件不存在或无权访问");
            }
            throw ex;
        }
    }

    private Task resolveTask(FileObject file) {
        if (file.getResourceType() == FileResourceType.TASK) {
            return taskPermissionService.requireViewable(file.getResourceId());
        }
        Comment comment = commentRepository.findById(file.getResourceId())
                .orElseThrow(() -> ApiException.notFound("文件不存在或无权访问"));
        return taskPermissionService.requireViewable(comment.getTask().getId());
    }

    private void requireParticipant(Task task) {
        SecurityUser actor = taskPermissionService.requireAuthenticated();
        projectPermissionService.assertNotArchived(task.getProject());
        if (projectPermissionService.roleOf(task.getProject().getId(), actor.id()) == null) {
            throw ApiException.forbidden("只有项目成员可以上传附件");
        }
    }

    private record ValidatedUpload(String originalName, String extension, String mimeType) {
    }

    private ValidatedUpload validate(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "不能上传空文件");
        }
        if (multipartFile.getSize() > maxFileSize) {
            throw new ApiException(ErrorCode.UNPROCESSABLE,
                    "文件超过大小限制（最大 " + (maxFileSize / 1024 / 1024) + " MB）");
        }
        String originalName = sanitizeName(multipartFile.getOriginalFilename());
        if (originalName.isEmpty()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "文件名不合法");
        }
        String extension = extensionOf(originalName);
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "出于安全考虑，禁止上传该类型的文件");
        }
        String mimeType = multipartFile.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }
        if (mimeType.length() > 150) {
            mimeType = mimeType.substring(0, 150);
        }
        return new ValidatedUpload(originalName, extension, mimeType);
    }

    /** 清洗原始文件名：去目录成分、去控制字符、限长（只作为元数据保存）。 */
    private String sanitizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.length() > MAX_ORIGINAL_NAME_LENGTH) {
            name = name.substring(name.length() - MAX_ORIGINAL_NAME_LENGTH);
        }
        return name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot + 1).toLowerCase();
        return extension.length() > 10 ? "" : extension.replaceAll("[^a-z0-9]", "");
    }
}