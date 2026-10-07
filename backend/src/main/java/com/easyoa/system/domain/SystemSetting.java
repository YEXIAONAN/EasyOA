package com.easyoa.system.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 系统设置（单组织键值配置）。
 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @Column(name = "key", length = 80)
    private String key;

    @Column(name = "value", nullable = false, length = 4000)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    protected SystemSetting() {
        // JPA
    }

    public SystemSetting(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public void updateValue(String value, Long updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}