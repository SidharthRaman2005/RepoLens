package com.example.backend.repository;

import com.example.backend.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositoryRepository extends JpaRepository<Repository, Long> {

	List<Repository> findAllByUserIdOrderByAnalyzedAtDesc(Long userId);

	Optional<Repository> findByIdAndUserId(Long id, Long userId);
}