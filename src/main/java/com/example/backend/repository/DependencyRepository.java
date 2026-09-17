package com.example.backend.repository;

import com.example.backend.entity.Dependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DependencyRepository extends JpaRepository<Dependency, Long> {

	List<Dependency> findAllBySourceEntityId(Long entityId);

	List<Dependency> findAllByTargetEntityId(Long entityId);

	List<Dependency> findAllBySourceEntityFileRepositoryId(Long repositoryId);

	long countBySourceEntityFileRepositoryId(Long repositoryId);

	@Modifying
	@Transactional
	void deleteAllBySourceEntityFileRepositoryId(Long repositoryId);
}