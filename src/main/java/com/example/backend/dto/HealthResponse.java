package com.example.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record HealthResponse(int overallScore, Categories categories, List<HealthIssueResponse> issues,
		List<String> recommendations, LocalDateTime analyzedAt) {

	public record Categories(int architecture, int documentation, int testing, int organization,
			int maintainability, int security) { }
}