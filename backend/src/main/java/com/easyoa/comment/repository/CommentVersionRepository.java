package com.easyoa.comment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.comment.domain.CommentVersion;

public interface CommentVersionRepository extends JpaRepository<CommentVersion, Long> {

    @Query("""
            select v from CommentVersion v
            left join fetch v.editor
            where v.comment.id = :commentId
            order by v.versionNo asc
            """)
    List<CommentVersion> findByCommentId(@Param("commentId") Long commentId);
}