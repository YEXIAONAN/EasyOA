package com.easyoa.comment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.comment.domain.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("""
            select c from Comment c
            join fetch c.author
            where c.id = :id
            """)
    Optional<Comment> findByIdWithAuthor(@Param("id") Long id);

    /** 顶层评论（分页，最新在前）。 */
    @Query(value = """
            select c from Comment c
            join fetch c.author
            where c.task.id = :taskId and c.parent is null
            order by c.createdAt desc, c.id desc
            """,
            countQuery = """
            select count(c) from Comment c
            where c.task.id = :taskId and c.parent is null
            """)
    Page<Comment> findTopLevel(@Param("taskId") Long taskId, Pageable pageable);

    /** 指定顶层评论的全部回复（升序）。 */
    @Query("""
            select c from Comment c
            join fetch c.author
            where c.parent.id in :parentIds
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findReplies(@Param("parentIds") Collection<Long> parentIds);

    long countByTaskId(Long taskId);

    /** Activity Feed：最近的有效评论（顶层、未撤回）。 */
    @Query("""
            select c from Comment c
            join fetch c.author
            join fetch c.task t
            join fetch t.project p
            where c.parent is null and c.withdrawnAt is null
              and (:scopeAll = true or p.id in :projectIds)
            order by c.createdAt desc, c.id desc
            """)
    List<Comment> findRecentForActivity(@Param("scopeAll") boolean scopeAll,
            @Param("projectIds") Collection<Long> projectIds, Pageable pageable);
}