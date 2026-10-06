package com.example.backend.dto;

import java.util.List;

public record ArchitectureModuleResponse(
		String id,
		String name,
		String path,
		int fileCount,
		int classCount,
		int dependencyCount,
		List<GraphNodeResponse> entities) {
}
