package com.easyoa.task.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.task.domain.AssignmentState;
import com.easyoa.task.domain.Task;
import com.easyoa.task.domain.TaskPriority;
import com.easyoa.task.domain.TaskStatusType;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            select t from Task t
            join fetch t.status
            join fetch t.project
            join fetch t.primaryAssignee
            left join fetch t.deputyAssignee
            where t.id = :id
            """)
    Optional<Task> findByIdWithDetails(@Param("id") Long id);

    /** 看板任务：项目内顶层任务，仅显示已生效派发的。 */
    @Query("""
            select t from Task t
            join fetch t.status
            join fetch t.project
            join fetch t.primaryAssignee
            left join fetch t.deputyAssignee
            where t.project.id = :projectId and t.parent is null and t.assignmentState = :state
            order by t.priority desc, t.plannedEndAt asc nulls last, t.id asc
            """)
    List<Task> findBoardTasks(@Param("projectId") Long projectId, @Param("state") AssignmentState state);

    /** 待派发审核任务（供 OWNER / DEPUTY_OWNER 审核）。 */
    @Query("""
            select t from Task t
            join fetch t.status
            join fetch t.primaryAssignee
            left join fetch t.deputyAssignee
            where t.project.id = :projectId and t.assignmentState = :state
            order by t.createdAt asc
            """)
    List<Task> findByProjectIdAndAssignmentState(@Param("projectId") Long projectId,
            @Param("state") AssignmentState state);

    /** 项目内任务列表（分页，仅顶层任务）。 */
    @Query(value = """
            select t from Task t
            join fetch t.status
            join fetch t.project
            join fetch t.primaryAssignee
            left join fetch t.deputyAssignee
            where t.project.id = :projectId
              and t.parent is null
              and t.assignmentState <> :excludedState
              and (:statusId is null or t.status.id = :statusId)
              and (:priority is null or t.priority = :priority)
              and (:keyword is null
                   or lower(t.title) like :keyword
                   or lower(coalesce(t.description, '')) like :keyword)
            order by t.updatedAt desc, t.id desc
            """,
            countQuery = """
            select count(t) from Task t
            where t.project.id = :projectId
              and t.parent is null
              and t.assignmentState <> :excludedState
              and (:statusId is null or t.status.id = :statusId)
              and (:priority is null or t.priority = :priority)
              and (:keyword is null
                   or lower(t.title) like :keyword
                   or lower(coalesce(t.description, '')) like :keyword)
            """)
    Page<Task> searchProjectTasks(@Param("projectId") Long projectId,
            @Param("excludedState") AssignmentState excludedState,
            @Param("statusId") Long statusId,
            @Param("priority") TaskPriority priority,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** 我的任务（主负责人 / 副负责人 / 协作成员）。 */
    @Query(value = """
            select t from Task t
            join fetch t.status
            join fetch t.project
            join fetch t.primaryAssignee
            left join fetch t.deputyAssignee
            where t.assignmentState = :state
              and (t.primaryAssignee.id = :userId
                   or t.deputyAssignee.id = :userId
                   or exists (select c.id from TaskCollaborator c where c.task = t and c.user.id = :userId))
              and (:openOnly = false or t.status.systemType not in :finishedTypes)
              and (:doneOnly = false or t.status.systemType in :finishedTypes)
              and (:dueSoon = false
                   or (t.status.systemType not in :finishedTypes and t.plannedEndAt <= :dueBefore))
              and (:keyword is null
                   or lower(t.title) like :keyword
                   or lower(t.project.name) like :keyword)
            order by t.plannedEndAt asc nulls last, t.updatedAt desc
            """,
            countQuery = """
            select count(t) from Task t
            where t.assignmentState = :state
              and (t.primaryAssignee.id = :userId
                   or t.deputyAssignee.id = :userId
                   or exists (select c.id from TaskCollaborator c where c.task = t and c.user.id = :userId))
              and (:openOnly = false or t.status.systemType not in :finishedTypes)
              and (:doneOnly = false or t.status.systemType in :finishedTypes)
              and (:dueSoon = false
                   or (t.status.systemType not in :finishedTypes and t.plannedEndAt <= :dueBefore))
              and (:keyword is null
                   or lower(t.title) like :keyword
                   or lower(t.project.name) like :keyword)
            """)
    Page<Task> searchMyTasks(@Param("userId") Long userId,
            @Param("state") AssignmentState state,
            @Param("openOnly") boolean openOnly,
            @Param("doneOnly") boolean doneOnly,
            @Param("finishedTypes") Collection<TaskStatusType> finishedTypes,
            @Param("dueSoon") boolean dueSoon,
            @Param("dueBefore") Instant dueBefore,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** 「我的任务」KPI：未结束的任务数。 */
    @Query("""
            select count(t) from Task t
            where t.assignmentState = :state
              and (t.primaryAssignee.id = :userId
                   or t.deputyAssignee.id = :userId
                   or exists (select c.id from TaskCollaborator c where c.task = t and c.user.id = :userId))
              and t.status.systemType not in :finishedTypes
            """)
    long countMyOpenTasks(@Param("userId") Long userId, @Param("state") AssignmentState state,
            @Param("finishedTypes") Collection<TaskStatusType> finishedTypes);

    /** 「即将到期」KPI：未结束且截止时间在阈值之前的任务数（含已逾期）。 */
    @Query("""
            select count(t) from Task t
            where t.assignmentState = :state
              and (t.primaryAssignee.id = :userId
                   or t.deputyAssignee.id = :userId
                   or exists (select c.id from TaskCollaborator c where c.task = t and c.user.id = :userId))
              and t.status.systemType not in :finishedTypes
              and t.plannedEndAt is not null
              and t.plannedEndAt <= :dueBefore
            """)
    long countMyDueSoonTasks(@Param("userId") Long userId, @Param("state") AssignmentState state,
            @Param("finishedTypes") Collection<TaskStatusType> finishedTypes,
            @Param("dueBefore") Instant dueBefore);

    // --- 子任务 -----------------------------------------------------------------

    @Query("""
            select t from Task t
            join fetch t.status
            join fetch t.primaryAssignee
            left join fetch t.deputyAssignee
            where t.parent.id = :parentId
            order by t.createdAt asc, t.id asc
            """)
    List<Task> findByParentIdWithStatus(@Param("parentId") Long parentId);

    long countByParentId(Long parentId);

    /** 子任务完成比例（进度 AUTO 模式）。 */
    @Query("""
            select count(t) from Task t
            where t.parent.id = :parentId and t.status.systemType = :systemType
            """)
    long countByParentIdAndStatusSystemType(@Param("parentId") Long parentId,
            @Param("systemType") TaskStatusType systemType);

    long countByProjectId(Long projectId);

    /** 即将截止扫描（未结束 + 计划截止在窗口内）。 */
    @Query("""
            select t from Task t
            join fetch t.primaryAssignee
            left join fetch t.deputyAssignee
            join fetch t.project
            where t.assignmentState = :state
              and t.status.systemType not in :finishedTypes
              and t.plannedEndAt is not null
              and t.plannedEndAt between :from and :to
            """)
    List<Task> findDueBetween(@Param("finishedTypes") Collection<TaskStatusType> finishedTypes,
            @Param("from") Instant from, @Param("to") Instant to, @Param("state") AssignmentState state);

    default List<Task> findDueBetween(Collection<TaskStatusType> finishedTypes, Instant from, Instant to) {
        return findDueBetween(finishedTypes, from, to, AssignmentState.ACTIVE);
    }

    /** 逾期扫描（未结束 + 计划截止已过）。 */
    @Query("""
            select t from Task t
            join fetch t.primaryAssignee
            left join fetch t.deputyAssignee
            join fetch t.project
            where t.assignmentState = :state
              and t.status.systemType not in :finishedTypes
              and t.plannedEndAt is not null
              and t.plannedEndAt < :now
            """)
    List<Task> findOverdue(@Param("finishedTypes") Collection<TaskStatusType> finishedTypes,
            @Param("now") Instant now, @Param("state") AssignmentState state);

    default List<Task> findOverdue(Collection<TaskStatusType> finishedTypes, Instant now) {
        return findOverdue(finishedTypes, now, AssignmentState.ACTIVE);
    }

    /** 全局搜索（数据范围：管理员全量，普通用户仅自己的项目）。 */
    @Query("""
            select t from Task t
            join fetch t.status
            join fetch t.project
            where t.assignmentState <> com.easyoa.task.domain.AssignmentState.REJECTED
              and (:scopeAll = true
                   or exists (select m.id from ProjectMember m
                              where m.project = t.project and m.user.id = :userId))
              and (lower(t.title) like :keyword or lower(coalesce(t.description, '')) like :keyword)
            order by t.updatedAt desc, t.id desc
            """)
    List<Task> searchForUser(@Param("scopeAll") boolean scopeAll, @Param("userId") Long userId,
            @Param("keyword") String keyword, Pageable pageable);

    /** Activity Feed：最近完成的任务。 */
    @Query("""
            select t from Task t
            join fetch t.project
            join fetch t.primaryAssignee
            where t.completedAt is not null
              and (:scopeAll = true or t.project.id in :projectIds)
            order by t.completedAt desc, t.id desc
            """)
    List<Task> findRecentCompleted(@Param("scopeAll") boolean scopeAll,
            @Param("projectIds") Collection<Long> projectIds, Pageable pageable);

    /** Activity Feed：最近创建的任务。 */
    @Query("""
            select t from Task t
            join fetch t.project
            join fetch t.primaryAssignee
            where (:scopeAll = true or t.project.id in :projectIds)
            order by t.createdAt desc, t.id desc
            """)
    List<Task> findRecentCreated(@Param("scopeAll") boolean scopeAll,
            @Param("projectIds") Collection<Long> projectIds, Pageable pageable);
}