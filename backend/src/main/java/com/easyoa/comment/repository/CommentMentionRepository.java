package com.easyoa.comment.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.comment.domain.CommentMention;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {

    @Query("""
            select m from CommentMention m
            join fetch m.user
            where m.comment.id in :commentIds
            order by m.id asc
            """)
    List<CommentMention> findByCommentIds(@Param("commentIds") Collection<Long> commentIds);
}