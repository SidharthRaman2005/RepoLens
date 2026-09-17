package com.example.backend.repository;

import com.example.backend.entity.CodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeEntityRepository extends JpaRepository<CodeEntity, Long> {

	List<CodeEntity> findAllByFileIdOrderByStartLineAsc(Long fileId);

	List<CodeEntity> findAllByFileRepositoryIdOrderByIdAsc(Long repositoryId);

	long countByFileRepositoryIdAndType(Long repositoryId, com.example.backend.entity.CodeEntityType type);

	void deleteAllByFileId(Long fileId);
}