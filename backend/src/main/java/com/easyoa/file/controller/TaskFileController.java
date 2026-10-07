package com.easyoa.file.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.file.application.FileService;
import com.easyoa.file.dto.FileView;

/**
 * 任务附件接口（上传 / 列表）。
 *
 * <p>上传需要项目成员身份；附件下载统一走 {@code GET /api/files/{fileId}}。
 */
@RestController
@RequestMapping("/api/tasks/{taskId}/files")
public class TaskFileController {

    private final FileService fileService;

    public TaskFileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public ApiResponse<List<FileView>> list(@PathVariable Long taskId) {
        return ApiResponse.ok(fileService.listTaskFiles(taskId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileView> upload(@PathVariable Long taskId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(fileService.uploadToTask(taskId, file));
    }
}