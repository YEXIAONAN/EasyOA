/**
 * 文件与附件模块（Phase 5 交付）。
 *
 * <p>规划内容：
 * <ul>
 *   <li>附件不得作为公开静态资源暴露，禁止 {@code /uploads/xxx.pdf} 形式访问；</li>
 *   <li>统一下载入口 {@code GET /api/files/{fileId}}：认证 → 资源权限 → 文件权限 → 下载；</li>
 *   <li>磁盘使用 UUID 随机存储名，元数据保存 original_name / stored_name / mime_type / size / sha256 /
 *       uploader / resource_type / resource_id；</li>
 *   <li>上传校验：大小限制、MIME 与扩展名双重校验、危险文件策略；</li>
 *   <li>敏感文件下载写入审计日志。</li>
 * </ul>
 */
package com.easyoa.file;