/**
 * 组织架构模块（Phase 2 交付）。
 *
 * <p>规划内容：
 * <ul>
 *   <li>{@code org_units}：邻接表组织树（parent_id），type = DEPARTMENT / TEAM；</li>
 *   <li>成员多组织归属（User N:N OrgUnit），且必须存在唯一主部门（Primary Department）；</li>
 *   <li>组织负责人、组织树查询（PostgreSQL WITH RECURSIVE）；</li>
 *   <li>组织页面：左侧组织树 + 右侧成员卡片，成员 Profile 按权限过滤敏感字段。</li>
 * </ul>
 */
package com.easyoa.organization;