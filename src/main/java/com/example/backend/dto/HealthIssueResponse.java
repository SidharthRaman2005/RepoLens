package com.example.backend.dto;

public record HealthIssueResponse(String category, String severity, String file,
		String message, String recommendation) { }