/**
 * 项目协作模块（Phase 3 交付）。
 *
 * <p>规划内容：
 * <ul>
 *   <li>项目生命周期：DRAFT / ACTIVE / PAUSED / COMPLETED / ARCHIVED（提供归档，不提供删除）；</li>
 *   <li>项目角色：OWNER × 1、DEPUTY_OWNER × 0~1、MEMBER × N；</li>
 *   <li>{@code ProjectPermissionService}：权限唯一入口，禁止散落 {@code if (userId == ownerId)}；</li>
 *   <li>项目详情：概览 / 看板 / 任务 / 时间线 / 成员 / 设置。</li>
 * </ul>
 */
package com.easyoa.project;