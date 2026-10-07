package com.easyoa.task.domain;

import java.time.Instant;

import com.easyoa.project.domain.Project;
import com.easyoa.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 任务（parent 非空时表示一级子任务）。
 *
 * <p>实体只负责状态与时间字段的自身一致性（如首次进入 ACTIVE / DONE 时记录时间），
 * 状态流校验、依赖阻塞、权限判断全部在应用服务层完成。
 *
 * <p>任务不做物理删除：结束使用状态（DONE / CLOSED），历史与审计保持完整。
 */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** 父任务：仅一级子任务，创建后不可变更。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id", updatable = false)
    private Task parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private TaskStatus status;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_assignee_id", nullable = false)
    private User primaryAssignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deputy_assignee_id")
    private User deputyAssignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_state", nullable = false, length = 30)
    private AssignmentState assignmentState = AssignmentState.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_mode", nullable = false, length = 20)
    private ProgressMode progressMode = ProgressMode.MANUAL;

    @Column(nullable = false)
    private int progress;

    @Column(name = "planned_start_at")
    private Instant plannedStartAt;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    /** 第一次从未开始进入 ACTIVE 状态时自动记录。 */
    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    /** 第一次进入 DONE 状态时自动记录。 */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Task() {
        // JPA
    }

    public Task(Project project, Task parent, TaskStatus status, String title, User primaryAssignee, Long createdBy) {
        this.project = project;
        this.parent = parent;
        this.status = status;
        this.title = title;
        this.primaryAssignee = primaryAssignee;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void updateInfo(String title, String description, TaskPriority priority, Instant plannedStartAt,
            Instant plannedEndAt) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.plannedStartAt = plannedStartAt;
        this.plannedEndAt = plannedEndAt;
    }

    /** 状态流转（流转合法性在服务层校验）；首次进入 ACTIVE / DONE 时自动记录实际时间。 */
    public void changeStatus(TaskStatus target, Instant now) {
        this.status = target;
        if (target.getSystemType() == TaskStatusType.ACTIVE && this.actualStartAt == null) {
            this.actualStartAt = now;
        }
        if (target.getSystemType() == TaskStatusType.DONE && this.completedAt == null) {
            this.completedAt = now;
        }
    }

    public void changeProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }

    public void changeProgressMode(ProgressMode mode) {
        this.progressMode = mode;
    }

    public void changeAssignees(User primaryAssignee, User deputyAssignee) {
        this.primaryAssignee = primaryAssignee;
        this.deputyAssignee = deputyAssignee;
    }

    /** 成员把任务派发给他人：等待项目负责人审核，审核前不生效。 */
    public void markPendingAssignment() {
        this.assignmentState = AssignmentState.PENDING_ASSIGNMENT;
    }

    public void activateAssignment() {
        this.assignmentState = AssignmentState.ACTIVE;
    }

    public void rejectAssignment() {
        this.assignmentState = AssignmentState.REJECTED;
    }

    public boolean isPendingAssignment() {
        return this.assignmentState == AssignmentState.PENDING_ASSIGNMENT;
    }

    public boolean isActiveAssignment() {
        return this.assignmentState == AssignmentState.ACTIVE;
    }

    public boolean isSubtask() {
        return this.parent != null;
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Task getParent() {
        return parent;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public User getPrimaryAssignee() {
        return primaryAssignee;
    }

    public User getDeputyAssignee() {
        return deputyAssignee;
    }

    public AssignmentState getAssignmentState() {
        return assignmentState;
    }

    public ProgressMode getProgressMode() {
        return progressMode;
    }

    public int getProgress() {
        return progress;
    }

    public Instant getPlannedStartAt() {
        return plannedStartAt;
    }

    public Instant getPlannedEndAt() {
        return plannedEndAt;
    }

    public Instant getActualStartAt() {
        return actualStartAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}