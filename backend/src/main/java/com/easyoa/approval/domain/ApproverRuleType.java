package com.easyoa.approval.domain;

/**
 * 动态审批人规则类型（发起时解析为实际审批人，随后生成快照，组织变化不再影响运行中的实例）。
 */
public enum ApproverRuleType {

    /** 固定成员（配置 userId）。 */
    FIXED_USER,

    /** 直属主管：主部门负责人；无则沿组织链逐级向上取最近的有负责人的单元。 */
    DIRECT_MANAGER,

    /** 主部门负责人（仅主部门一层，不向上递补）。 */
    PRIMARY_DEPT_MANAGER,

    /** 组织负责人：与 DIRECT_MANAGER 相同的组织链解析（规范保留枚举）。 */
    ORG_UNIT_MANAGER,

    /** 项目负责人（表单字段 projectField 指定项目 id）。 */
    PROJECT_OWNER,

    /** 项目副负责人（表单字段 projectField 指定项目 id）。 */
    PROJECT_DEPUTY,

    /** 系统角色（配置 systemRole：ADMIN / ROOT）。 */
    SYSTEM_ROLE
}