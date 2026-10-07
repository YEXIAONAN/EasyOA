package com.easyoa.system.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.system.domain.SystemSetting;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    /**
     * 原子性的「仅当值等于期望值时才更新」，用于首次初始化的并发互斥：
     * 只有把 setup.completed 从 false 改为 true 的那一个请求可以初始化成功。
     *
     * @return 受影响行数，0 表示并发下已被其他请求抢先完成
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SystemSetting s set s.value = :newValue, s.updatedAt = :now, s.updatedBy = :actorId "
            + "where s.key = :key and s.value = :expectedValue")
    int updateValueIfMatches(@Param("key") String key,
            @Param("expectedValue") String expectedValue,
            @Param("newValue") String newValue,
            @Param("now") Instant now,
            @Param("actorId") Long actorId);
}