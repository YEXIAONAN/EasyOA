package com.easyoa.task.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.task.domain.TaskStatus;
import com.easyoa.task.domain.TaskStatusType;

public interface TaskStatusRepository extends JpaRepository<TaskStatus, Long> {

    List<TaskStatus> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    boolean existsByProjectId(Long projectId);

    Optional<TaskStatus> findByIdAndProjectId(Long id, Long projectId);

    Optional<TaskStatus> findFirstByProjectIdAndSystemTypeOrderBySortOrderAsc(Long projectId, TaskStatusType systemType);

    @Query("select coalesce(max(s.sortOrder), -1) from TaskStatus s where s.project.id = :projectId")
    int maxSortOrder(@Param("projectId") Long projectId);
}