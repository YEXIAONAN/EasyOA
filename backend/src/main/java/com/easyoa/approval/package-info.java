/**
 * 审批模块（Phase 6 交付）。
 *
 * <p>规划内容：
 * <ul>
 *   <li>模板化审批 + 可配置节点（不做拖拽式 BPMN 编辑器）；模板历史版本，运行中的实例使用发起时版本；</li>
 *   <li>自定义表单字段类型：TEXT / NUMBER / MONEY / DATE / SELECT / USER / ATTACHMENT 等，保存表单快照；</li>
 *   <li>审批实例状态机：DRAFT / PENDING / APPROVED / REJECTED / RETURNED / CANCELLED；</li>
 *   <li>多人审批规则：ANY_ONE / ALL；动态审批人与替补链（禁止自我审批，无法解析合法审批人时禁止提交）；</li>
 *   <li>转交（Transfer）与完整审计。</li>
 * </ul>
 */
package com.easyoa.approval;