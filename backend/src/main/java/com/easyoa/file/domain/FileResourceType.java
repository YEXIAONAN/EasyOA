package com.easyoa.file.domain;

/**
 * 附件的资源归属类型（files 表使用资源泛化：resource_type + resource_id）。
 */
public enum FileResourceType {

    /** 任务附件（resource_id = task_id）。 */
    TASK,

    /** 评论附件（resource_id = comment_id）。 */
    COMMENT,

    /** 审批附件（resource_id = instance_id；0 表示尚未提交的申请草稿附件）。 */
    APPROVAL
}