package com.easyoa.approval.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.easyoa.approval.domain.ApproverRuleType;
import com.easyoa.approval.domain.NodeMode;
import com.easyoa.approval.dto.ApproverRuleView;
import com.easyoa.approval.dto.FormFieldView;
import com.easyoa.approval.dto.NodeDefinitionView;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 审批 schema 编解码与校验。
 *
 * <p>模板表单 / 节点定义与实例表单快照都以 JSON 存储（数据库 text 列），
 * 这里统一负责序列化、反序列化与结构校验，避免业务代码散落 JSON 处理。
 */
@Service
public class ApprovalSchemaCodec {

    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "MONEY", "DATE", "DATETIME", "SELECT", "MULTI_SELECT", "USER", "ATTACHMENT");

    private static final Set<String> SYSTEM_ROLES = Set.of("ADMIN", "ROOT");

    private static final int MAX_FALLBACK_DEPTH = 3;

    private final ObjectMapper objectMapper;

    public ApprovalSchemaCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // --- 序列化 / 反序列化 ---------------------------------------------------------

    public String writeFormSchema(List<FormFieldView> fields) {
        return write(fields);
    }

    public String writeNodeSchema(List<NodeDefinitionView> nodes) {
        return write(nodes);
    }

    public List<FormFieldView> readFormSchema(String json) {
        return read(json, new TypeReference<List<FormFieldView>>() {
        });
    }

    public List<NodeDefinitionView> readNodeSchema(String json) {
        return read(json, new TypeReference<List<NodeDefinitionView>>() {
        });
    }

    /** 实例表单快照：{"schema":[...],"values":{...}} */
    public String writeSnapshot(List<FormFieldView> fields, Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(Map.of("schema", fields, "values", values));
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "表单快照序列化失败");
        }
    }

    public record Snapshot(List<FormFieldView> schema, Map<String, Object> values) {
    }

    public Snapshot readSnapshot(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<FormFieldView> schema = objectMapper.convertValue(root.path("schema"),
                    new TypeReference<List<FormFieldView>>() {
                    });
            Map<String, Object> values = objectMapper.convertValue(root.path("values"),
                    new TypeReference<Map<String, Object>>() {
                    });
            return new Snapshot(schema, values);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "表单快照解析失败");
        }
    }

    // --- 结构校验 ---------------------------------------------------------------

    /** 校验模板 schema 结构（字段 key 唯一、类型合法、节点与审批人规则完整）。 */
    public void validateSchema(List<FormFieldView> fields, List<NodeDefinitionView> nodes) {
        Set<String> keys = new HashSet<>();
        for (FormFieldView field : fields) {
            if (field.key() == null || field.key().isBlank()) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "表单字段缺少标识（key）");
            }
            if (!keys.add(field.key())) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "表单字段标识重复：" + field.key());
            }
            if (field.label() == null || field.label().isBlank()) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "表单字段缺少名称：" + field.key());
            }
            if (field.type() == null || !FIELD_TYPES.contains(field.type())) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "不支持的表单字段类型：" + field.type());
            }
        }
        for (NodeDefinitionView node : nodes) {
            if (node.name() == null || node.name().isBlank()) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "审批节点缺少名称");
            }
            if (node.mode() == null) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "审批节点缺少通过模式（ANY_ONE / ALL）");
            }
            if (node.approvers() == null || node.approvers().isEmpty()) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "审批节点「" + node.name() + "」未配置审批人");
            }
            for (ApproverRuleView rule : node.approvers()) {
                validateRule(rule, node.name(), 0);
            }
        }
    }

    private void validateRule(ApproverRuleView rule, String nodeName, int depth) {
        if (rule == null || rule.type() == null) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "审批节点「" + nodeName + "」存在无效的审批人规则");
        }
        if (depth > MAX_FALLBACK_DEPTH) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "备用审批规则嵌套过深");
        }
        switch (rule.type()) {
            case FIXED_USER -> {
                if (rule.userId() == null) {
                    throw new ApiException(ErrorCode.UNPROCESSABLE, "固定审批人规则缺少成员");
                }
            }
            case SYSTEM_ROLE -> {
                if (rule.systemRole() == null || !SYSTEM_ROLES.contains(rule.systemRole())) {
                    throw new ApiException(ErrorCode.UNPROCESSABLE, "系统角色规则只支持 ADMIN / ROOT");
                }
            }
            case PROJECT_OWNER, PROJECT_DEPUTY -> {
                if (rule.projectField() == null || rule.projectField().isBlank()) {
                    throw new ApiException(ErrorCode.UNPROCESSABLE, "项目负责人规则需要指定表单中的项目字段");
                }
            }
            case DIRECT_MANAGER, PRIMARY_DEPT_MANAGER, ORG_UNIT_MANAGER -> {
                // 无需额外配置
            }
        }
        if (rule.fallback() != null) {
            for (ApproverRuleView fallback : rule.fallback()) {
                validateRule(fallback, nodeName, depth + 1);
            }
        }
    }

    /**
     * 校验表单值：必填、类型、选项范围；拒绝未定义字段。
     *
     * <p>USER / ATTACHMENT 的深层校验（成员有效性、附件归属）在服务层完成。
     */
    public void validateValues(List<FormFieldView> fields, Map<String, Object> values) {
        Map<String, Object> safeValues = values == null ? Map.of() : values;
        Set<String> definedKeys = new HashSet<>();
        for (FormFieldView field : fields) {
            definedKeys.add(field.key());
            Object value = safeValues.get(field.key());
            boolean empty = value == null
                    || (value instanceof String text && text.isBlank())
                    || (value instanceof List<?> list && list.isEmpty());
            if (empty) {
                if (field.required()) {
                    throw new ApiException(ErrorCode.UNPROCESSABLE, "请填写「" + field.label() + "」");
                }
                continue;
            }
            switch (field.type()) {
                case "NUMBER", "MONEY" -> {
                    if (!(value instanceof Number)) {
                        throw new ApiException(ErrorCode.UNPROCESSABLE,
                                "「" + field.label() + "」必须是数字");
                    }
                }
                case "SELECT" -> {
                    if (!(value instanceof String text)) {
                        throw new ApiException(ErrorCode.UNPROCESSABLE,
                                "「" + field.label() + "」必须是选项之一");
                    }
                    if (field.options() != null && !field.options().isEmpty() && !field.options().contains(text)) {
                        throw new ApiException(ErrorCode.UNPROCESSABLE,
                                "「" + field.label() + "」的选项不合法");
                    }
                }
                case "MULTI_SELECT" -> {
                    if (!(value instanceof List<?> list)) {
                        throw new ApiException(ErrorCode.UNPROCESSABLE,
                                "「" + field.label() + "」必须是多选列表");
                    }
                    if (field.options() != null && !field.options().isEmpty()) {
                        for (Object item : list) {
                            if (item == null || !field.options().contains(String.valueOf(item))) {
                                throw new ApiException(ErrorCode.UNPROCESSABLE,
                                        "「" + field.label() + "」的选项不合法");
                            }
                        }
                    }
                }
                case "USER" -> {
                    if (!(value instanceof Number)) {
                        throw new ApiException(ErrorCode.UNPROCESSABLE,
                                "「" + field.label() + "」必须是成员");
                    }
                }
                case "ATTACHMENT" -> {
                    if (!(value instanceof List<?>)) {
                        throw new ApiException(ErrorCode.UNPROCESSABLE,
                                "「" + field.label() + "」必须是附件列表");
                    }
                }
                default -> {
                    if (!(value instanceof String)) {
                        throw new ApiException(ErrorCode.UNPROCESSABLE,
                                "「" + field.label() + "」格式不正确");
                    }
                }
            }
        }
        for (String key : safeValues.keySet()) {
            if (!definedKeys.contains(key)) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "表单包含未定义的字段：" + key);
            }
        }
    }

    // --- 内部方法 ---------------------------------------------------------------

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "审批 schema 序列化失败");
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "审批 schema 解析失败");
        }
    }
}