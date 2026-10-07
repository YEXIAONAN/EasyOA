package com.easyoa.file.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.file.domain.FileObject;
import com.easyoa.file.domain.FileResourceType;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    @Query("""
            select f from FileObject f
            join fetch f.uploader
            where f.resourceType = :resourceType and f.resourceId = :resourceId and f.deletedAt is null
            order by f.createdAt asc, f.id asc
            """)
    List<FileObject> findActiveByResource(@Param("resourceType") FileResourceType resourceType,
            @Param("resourceId") Long resourceId);

    @Query("""
            select f from FileObject f
            join fetch f.uploader
            where f.resourceType = :resourceType and f.resourceId in :resourceIds and f.deletedAt is null
            order by f.createdAt asc, f.id asc
            """)
    List<FileObject> findActiveByResources(@Param("resourceType") FileResourceType resourceType,
            @Param("resourceIds") Collection<Long> resourceIds);

    @Query("""
            select f from FileObject f
            join fetch f.uploader
            where f.id = :id
            """)
    Optional<FileObject> findByIdWithUploader(@Param("id") Long id);
}