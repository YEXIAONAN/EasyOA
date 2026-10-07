-- =============================================================================
-- V8：安全（TOTP / 安全策略 / 安全事件索引）
--
-- 说明：
--   1. TOTP 复用 users.totp_enabled / users.totp_secret_encrypted（V1 已预留），
--      未启用时该列保存「待确认」的加密 Secret，确认绑定后才置 totp_enabled = true。
--   2. 安全策略复用 system_settings（KV），与初始化开关同源，避免新增配置表。
--   3. security_events 追加角色维度索引，便于按操作者检索。
-- =============================================================================

-- 安全策略默认值（可由 ROOT 通过高危操作通道修改）
insert into system_settings (key, value) values ('security.login_max_failures', '5')
on conflict (key) do nothing;
insert into system_settings (key, value) values ('security.login_lock_minutes', '15')
on conflict (key) do nothing;
insert into system_settings (key, value) values ('security.password_min_length', '10')
on conflict (key) do nothing;
insert into system_settings (key, value) values ('security.totp_required_for_admins', 'false')
on conflict (key) do nothing;

-- 安全事件按操作者检索
create index if not exists ix_security_events_actor_time on security_events (actor_user_id, created_at desc);
