package com.example.backend.repository;

import com.example.backend.entity.HealthIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface HealthIssueRepository extends JpaRepository<HealthIssue, Long> {

	@Modifying
	@Transactional
	void deleteAllByAnalysisRepositoryId(Long repositoryId);
}