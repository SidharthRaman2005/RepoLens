package com.example.backend.controller;

import com.example.backend.dto.GraphEdgeResponse;
import com.example.backend.dto.GraphNodeResponse;
import com.example.backend.dto.GraphResponse;
import com.example.backend.service.DependencyGraphService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories/{repositoryId}")
public class DependencyGraphController {

	private final DependencyGraphService graphService;

	public DependencyGraphController(DependencyGraphService graphService) {
		this.graphService = graphService;
	}

	@GetMapping("/graph")
	public GraphResponse graph(@PathVariable Long repositoryId, Authentication authentication) {
		return graphService.graph(repositoryId, authentication.getName());
	}

	@GetMapping("/dependencies/{entityId}")
	public List<GraphEdgeResponse> dependencies(@PathVariable Long repositoryId, @PathVariable Long entityId,
			Authentication authentication) {
		return graphService.dependencies(repositoryId, entityId, authentication.getName());
	}

	@GetMapping("/dependents/{entityId}")
	public List<GraphEdgeResponse> dependents(@PathVariable Long repositoryId, @PathVariable Long entityId,
			Authentication authentication) {
		return graphService.dependents(repositoryId, entityId, authentication.getName());
	}

	@GetMapping("/impact/{entityId}")
	public List<GraphNodeResponse> impact(@PathVariable Long repositoryId, @PathVariable Long entityId,
			Authentication authentication) {
		return graphService.impact(repositoryId, entityId, authentication.getName());
	}
}