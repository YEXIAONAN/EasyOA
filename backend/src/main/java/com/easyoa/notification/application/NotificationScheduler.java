package com.easyoa.notification.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.notification.domain.NotificationType;
import com.easyoa.notification.repository.NotificationRepository;
import com.easyoa.task.domain.Task;
import com.easyoa.task.domain.TaskStatusType;
import com.easyoa.task.repository.TaskRepository;
import com.easyoa.user.domain.User;

/**
 * 任务期限扫描：生成「即将截止」与「已逾期」通知。
 *
 * <p>按「收件人 + 类型 + 任务」去重（存在未读则不重复发送）；
 * 收件人为任务主负责人与副负责人。可通过
 * {@code easyoa.notifications.scheduler.enabled=false} 关闭（集成测试环境默认关闭）。
 */
@Component
@ConditionalOnProperty(prefix = "easyoa.notifications.scheduler", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private static final EnumSet<TaskStatusType> FINISHED_TYPES = EnumSet.of(TaskStatusType.DONE,
            TaskStatusType.CLOSED);

    private static final int DUE_SOON_HOURS = 24;

    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public NotificationScheduler(TaskRepository taskRepository, NotificationRepository notificationRepository,
            NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${easyoa.notifications.scheduler.interval-ms:1800000}",
            initialDelayString = "${easyoa.notifications.scheduler.initial-delay-ms:60000}")
    @Transactional
    public void scanTaskDeadlines() {
        Instant now = Instant.now();
        List<Task> dueSoon = taskRepository.findDueBetween(FINISHED_TYPES, now,
                now.plus(DUE_SOON_HOURS, ChronoUnit.HOURS));
        int sent = 0;
        sent += notifyDeadline(dueSoon, NotificationType.TASK_DUE_SOON, "任务即将截止");
        List<Task> overdue = taskRepository.findOverdue(FINISHED_TYPES, now);
        sent += notifyDeadline(overdue, NotificationType.TASK_OVERDUE, "任务已逾期");
        if (sent > 0) {
            log.info("任务期限扫描完成：新增通知 {} 条（即将截止 {} / 逾期 {}）", sent, dueSoon.size(),
                    overdue.size());
        }
    }

    private int notifyDeadline(List<Task> tasks, NotificationType type, String titlePrefix) {
        int sent = 0;
        for (Task task : tasks) {
            List<User> recipients = new ArrayList<>();
            recipients.add(task.getPrimaryAssignee());
            if (task.getDeputyAssignee() != null) {
                recipients.add(task.getDeputyAssignee());
            }
            String link = "/projects/" + task.getProject().getId() + "/board?task=" + task.getId();
            String body = "计划截止：" + task.getPlannedEndAt();
            for (User recipient : recipients) {
                if (recipient == null) {
                    continue;
                }
                if (notificationRepository.existsByRecipientIdAndTypeAndResourceTypeAndResourceIdAndReadAtIsNull(
                        recipient.getId(), type, "TASK", task.getId())) {
                    continue;
                }
                notificationService.notify(recipient.getId(), type, titlePrefix + "「" + task.getTitle() + "」",
                        body, link, "TASK", task.getId(), null);
                sent++;
            }
        }
        return sent;
    }
}