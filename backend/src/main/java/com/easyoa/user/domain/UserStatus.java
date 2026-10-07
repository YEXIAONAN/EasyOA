package com.easyoa.user.domain;

/**
 * 用户状态。
 *
 * <p>业务对象优先 Deactivate 而非物理删除（见数据删除原则）。
 */
public enum UserStatus {

    ACTIVE,
    DISABLED
}