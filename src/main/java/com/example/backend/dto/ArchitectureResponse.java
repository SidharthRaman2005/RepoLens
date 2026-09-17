package com.example.backend.dto;

import java.util.List;

public record ArchitectureResponse(
		String architecture,
		double confidence,
		String confidenceLevel,
		String explanation,
		List<String> evidence) {
}