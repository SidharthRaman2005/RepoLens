package com.example.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
		RepositorySummary repository,
		Statistics statistics,
		List<LanguageStatistics> languages,
		ArchitectureSummary architecture,
		AnalysisSummary analysis,
		String summary) {

	public record RepositorySummary(String name, String owner, String description,
			long stars, long forks, String language) { }

	public record Statistics(long fileCount, long sourceFileCount, long classCount,
			long interfaceCount, long methodCount, long functionCount, long dependencyCount) { }

	public record LanguageStatistics(String language, long files, double percentage) { }

	public record ArchitectureSummary(String name, double confidence) { }

	public record AnalysisSummary(String status, LocalDateTime analyzedAt) { }
}