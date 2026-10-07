package com.easyoa.organization;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.easyoa.AbstractIntegrationTest;
import com.easyoa.user.domain.SystemRole;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 组织架构集成测试：组织树、循环检测、归档规则、成员归属与主部门不变式、
 * 成员目录数据范围、档案联系方式过滤、负责人委托管理。
 */
class OrganizationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("普通成员不能调整组织架构（403）")
    void memberCannotManageOrgStructure() throws Exception {
        initializeSystemWithRoot("root");
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/org-units")
                        .session(memberSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "技术部", "type", "DEPARTMENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("组织树按层级返回，含 depth / 负责人 / 成员数")
    void treeReturnsHierarchy() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        Long tech = createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);
        createUnitViaApi(rootSession, "后端组", "TEAM", tech);

        mockMvc.perform(get("/api/org-units").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("技术部"))
                .andExpect(jsonPath("$.data[0].depth").value(0))
                .andExpect(jsonPath("$.data[0].children.length()").value(1))
                .andExpect(jsonPath("$.data[0].children[0].name").value("后端组"))
                .andExpect(jsonPath("$.data[0].children[0].depth").value(1));
    }

    @Test
    @DisplayName("同一层级下不允许同名组织单元（409）")
    void duplicateNameInSameLevelIsRejected() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);

        mockMvc.perform(post("/api/org-units")
                        .session(rootSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "技术部", "type", "DEPARTMENT"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("移动组织单元：禁止移动到自身或下级（防成环）")
    void moveRejectsCycles() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        Long tech = createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);
        Long backend = createUnitViaApi(rootSession, "后端组", "TEAM", tech);
        Long apiGroup = createUnitViaApi(rootSession, "接口组", "TEAM", backend);

        // 移动到自身
        mockMvc.perform(post("/api/org-units/{id}/move", tech)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newParentId", tech))))
                .andExpect(status().isConflict());

        // 移动到直接下级
        mockMvc.perform(post("/api/org-units/{id}/move", tech)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newParentId", backend))))
                .andExpect(status().isConflict());

        // 移动到间接下级（孙节点）
        mockMvc.perform(post("/api/org-units/{id}/move", tech)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newParentId", apiGroup))))
                .andExpect(status().isConflict());

        // 合法移动：接口组挂到技术部之下
        mockMvc.perform(post("/api/org-units/{id}/move", apiGroup)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newParentId", tech))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(tech));
    }

    @Test
    @DisplayName("归档规则：存在未归档下级时禁止归档；归档后默认从树中隐藏，可恢复")
    void archiveRulesAndRestore() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        Long tech = createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);
        Long backend = createUnitViaApi(rootSession, "后端组", "TEAM", tech);

        // 有未归档下级 → 拒绝
        mockMvc.perform(post("/api/org-units/{id}/archive", tech).session(rootSession).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/org-units/{id}/archive", backend).session(rootSession).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/org-units/{id}/archive", tech).session(rootSession).with(csrf()))
                .andExpect(status().isOk());

        // 默认隐藏已归档单元
        mockMvc.perform(get("/api/org-units").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 含归档视图可见
        mockMvc.perform(get("/api/org-units?includeArchived=true").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ARCHIVED"));

        // 上级仍归档时不能单独恢复下级
        mockMvc.perform(post("/api/org-units/{id}/restore", backend).session(rootSession).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/org-units/{id}/restore", tech).session(rootSession).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/org-units/{id}/restore", backend).session(rootSession).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("成员归属与主部门：首个归属自动成为主部门，移除主部门后自动递补")
    void membershipPrimaryInvariant() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long tech = createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);
        Long backend = createUnitViaApi(rootSession, "后端组", "TEAM", tech);

        // 首个归属 → 主部门
        mockMvc.perform(post("/api/org-units/{id}/members", backend)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].primary").value(true));

        // 第二个归属不改变主部门
        mockMvc.perform(post("/api/org-units/{id}/members", tech)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == %d)].primary".formatted(memberId)).value(false));

        // 切换主部门
        mockMvc.perform(put("/api/org-units/{id}/members/{userId}/primary", tech, memberId)
                        .session(rootSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.primary == true)].orgUnit.name").value("技术部"));

        // 移除主部门 → 剩余归属自动递补为主部门
        mockMvc.perform(delete("/api/org-units/{id}/members/{userId}", tech, memberId)
                        .session(rootSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/org-units/{id}/members", backend).session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].primary").value(true));
    }

    @Test
    @DisplayName("成员目录：组织筛选自动包含下级单元，关键字与状态生效")
    void directoryScopesBySubtree() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long tech = createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);
        Long backend = createUnitViaApi(rootSession, "后端组", "TEAM", tech);
        Long productDept = createUnitViaApi(rootSession, "产品部", "DEPARTMENT", null);

        mockMvc.perform(post("/api/org-units/{id}/members", backend)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", memberId))))
                .andExpect(status().isOk());

        // 按上级部门筛选（应包含下级单元成员）
        mockMvc.perform(get("/api/users/directory").param("orgUnitId", String.valueOf(tech)).session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("member"))
                .andExpect(jsonPath("$.data.items[0].primaryOrgUnit.name").value("后端组"));

        // 无成员的组织
        mockMvc.perform(get("/api/users/directory").param("orgUnitId", String.valueOf(productDept)).session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 关键字过滤
        mockMvc.perform(get("/api/users/directory").param("keyword", "member").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/users/directory").param("keyword", "no-such-person").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("成员档案：联系方式按权限过滤（本人 / 管理员 / 所在组织负责人可见）")
    void profileFiltersContactInfo() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", "member@easyoa.dev", "13800000000");
        Long kevinId = createUserViaApi(rootSession, "kevin", "Kevin", "MEMBER", null, null);
        Long tech = createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);

        // 本人可见
        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/users/{id}", memberId).session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactVisible").value(true))
                .andExpect(jsonPath("$.data.contact.email").value("member@easyoa.dev"));

        // 普通同事不可见
        MockHttpSession kevinSession = login("kevin", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/users/{id}", memberId).session(kevinSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactVisible").value(false))
                .andExpect(jsonPath("$.data.contact").doesNotExist())
                .andExpect(jsonPath("$.data.displayName").value("Member"));

        // 管理员可见
        mockMvc.perform(get("/api/users/{id}", memberId).session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactVisible").value(true));

        // 所在组织负责人可见（把 member 设为技术部负责人，再让他查看 kevin 的档案）
        mockMvc.perform(post("/api/org-units/{id}/members", tech)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", memberId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/org-units/{id}/members", tech)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", kevinId))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/org-units/{id}", tech)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "技术部", "type", "DEPARTMENT", "managerUserId", memberId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/{id}", kevinId).session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactVisible").value(true));
    }

    @Test
    @DisplayName("组织负责人可以管理自己单元的成员，但不能管理其他单元")
    void managerCanManageOwnUnitMembers() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long managerId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long otherId = createUserViaApi(rootSession, "kevin", "Kevin", "MEMBER", null, null);
        Long tech = createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);
        Long backend = createUnitViaApi(rootSession, "后端组", "TEAM", tech);
        Long productDept = createUnitViaApi(rootSession, "产品部", "DEPARTMENT", null);

        // 把 member 设为技术部负责人（后端组在其下）
        mockMvc.perform(put("/api/org-units/{id}", tech)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "技术部", "type", "DEPARTMENT", "managerUserId", managerId))))
                .andExpect(status().isOk());

        MockHttpSession managerSession = login("member", DEFAULT_PASSWORD);

        // 可以管理下级单元的成员
        mockMvc.perform(post("/api/org-units/{id}/members", backend)
                        .session(managerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", otherId))))
                .andExpect(status().isOk());

        // 不能管理无关单元的成员
        mockMvc.perform(post("/api/org-units/{id}/members", productDept)
                        .session(managerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", otherId))))
                .andExpect(status().isForbidden());
    }
}