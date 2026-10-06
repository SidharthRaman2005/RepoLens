package com.example.backend.controller;

import com.example.backend.dto.ExploreResponse;
import com.example.backend.service.ExploreService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/explore")
public class ExploreController {

	private final ExploreService exploreService;

	public ExploreController(ExploreService exploreService) {
		this.exploreService = exploreService;
	}

	@GetMapping
	public ExploreResponse explore(@PathVariable Long repositoryId, Authentication authentication) {
		return exploreService.explore(repositoryId, authentication.getName());
	}
}
