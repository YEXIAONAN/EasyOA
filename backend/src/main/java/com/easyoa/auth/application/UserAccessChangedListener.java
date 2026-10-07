package com.easyoa.auth.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.easyoa.user.domain.event.UserAccessChangedEvent;

/**
 * 监听用户访问权限变化事件，撤销其全部会话。
 *
 * <p>使用 {@code AFTER_COMMIT} + 独立事务：只有账号变更真正提交后才会执行会话撤销，
 * 且撤销动作不受原事务回滚影响。
 */
@Component
public class UserAccessChangedListener {

    private final SessionService sessionService;

    public UserAccessChangedListener(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserAccessChanged(UserAccessChangedEvent event) {
        sessionService.revokeAllForUser(event.userId(), event.reason(), null);
    }
}