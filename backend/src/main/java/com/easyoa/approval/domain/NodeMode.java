package com.easyoa.approval.domain;

/**
 * 审批节点通过模式。
 *
 * <ul>
 *   <li>{@link #ANY_ONE} — 任意一人通过即可；</li>
 *   <li>{@link #ALL} — 所有审批人通过才通过（任一人拒绝即拒绝）。</li>
 * </ul>
 *
 * <p>v0.1.0 不实现 2/3、60% Vote 等复杂规则。
 */
public enum NodeMode {

    ANY_ONE,
    ALL
}