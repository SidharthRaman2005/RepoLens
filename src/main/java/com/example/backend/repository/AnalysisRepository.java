package com.example.backend.repository;

import com.example.backend.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	Optional<Analysis> findTopByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

	@Modifying
	@Transactional
	void deleteAllByRepositoryId(Long repositoryId);
}