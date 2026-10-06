package com.example.backend.dto;

import java.util.List;

public record GraphResponse(
		List<GraphNodeResponse> nodes,
		List<GraphEdgeResponse> edges,
		List<ArchitectureModuleResponse> modules,
		List<ModuleEdgeResponse> moduleEdges) {

	public GraphResponse(List<GraphNodeResponse> nodes, List<GraphEdgeResponse> edges) {
		this(nodes, edges, List.of(), List.of());
	}
}