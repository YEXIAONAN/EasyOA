package com.easyoa.task.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.task.domain.TaskDependency;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {

    /** 某任务的前置依赖（含前置任务的状态，用于阻塞判断与展示）。 */
    @Query("""
            select d from TaskDependency d
            join fetch d.dependsOnTask t
            join fetch t.status
            left join fetch t.primaryAssignee
            where d.task.id = :taskId
            order by d.createdAt asc, d.id asc
            """)
    List<TaskDependency> findByTaskIdWithTarget(@Param("taskId") Long taskId);

    /** 项目内全部依赖边（循环检测与看板 blocked 批量计算）。 */
    @Query("""
            select d from TaskDependency d
            join fetch d.dependsOnTask t
            join fetch t.status
            where d.project.id = :projectId
            """)
    List<TaskDependency> findByProjectIdWithTarget(@Param("projectId") Long projectId);

    Optional<TaskDependency> findByTaskIdAndDependsOnTaskId(Long taskId, Long dependsOnTaskId);

    long countByTaskId(Long taskId);
}