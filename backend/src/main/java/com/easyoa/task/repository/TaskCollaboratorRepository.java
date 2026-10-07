package com.easyoa.task.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.task.domain.TaskCollaborator;

public interface TaskCollaboratorRepository extends JpaRepository<TaskCollaborator, Long> {

    @Query("""
            select c from TaskCollaborator c
            join fetch c.user
            where c.task.id = :taskId
            order by c.addedAt asc, c.id asc
            """)
    List<TaskCollaborator> findByTaskIdWithUser(@Param("taskId") Long taskId);

    List<TaskCollaborator> findByTaskId(Long taskId);
}