package com.example.backend.dto;

public record RepositoryResponse(
		Long id,
		String owner,
		String name,
		String url,
		String description,
		String defaultBranch,
		long stars,
		long forks,
		String language,
		String readme,
		java.time.LocalDateTime analyzedAt,
		String processingStatus) {
}