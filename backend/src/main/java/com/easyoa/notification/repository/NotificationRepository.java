package com.easyoa.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.notification.domain.Notification;
import com.easyoa.notification.domain.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(value = """
            select n from Notification n
            left join fetch n.actor
            where n.recipient.id = :recipientId
              and (:unreadOnly = false or n.readAt is null)
            order by n.createdAt desc, n.id desc
            """,
            countQuery = """
            select count(n) from Notification n
            where n.recipient.id = :recipientId
              and (:unreadOnly = false or n.readAt is null)
            """)
    Page<Notification> search(@Param("recipientId") Long recipientId, @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.recipient.id = :recipientId and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") java.time.Instant now);

    /** 定时扫描去重：同收件人 + 同类型 + 同资源仍存在未读通知则不重复发送。 */
    boolean existsByRecipientIdAndTypeAndResourceTypeAndResourceIdAndReadAtIsNull(Long recipientId,
            NotificationType type, String resourceType, Long resourceId);

    /** 批量去重：返回该类型 + 该资源下已存在未读通知的资源 id。 */
    @Query("""
            select n.resourceId from Notification n
            where n.recipient.id = :recipientId and n.type = :type
              and n.resourceType = :resourceType and n.readAt is null
              and n.resourceId in :resourceIds
            """)
    List<Long> findUnreadResourceIds(@Param("recipientId") Long recipientId, @Param("type") NotificationType type,
            @Param("resourceType") String resourceType, @Param("resourceIds") List<Long> resourceIds);
}