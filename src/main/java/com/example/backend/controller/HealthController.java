package com.example.backend.controller;

import com.example.backend.dto.HealthResponse;
import com.example.backend.service.HealthAnalysisService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/health")
public class HealthController {

	private final HealthAnalysisService healthAnalysisService;

	public HealthController(HealthAnalysisService healthAnalysisService) {
		this.healthAnalysisService = healthAnalysisService;
	}

	@GetMapping
	public HealthResponse health(@PathVariable Long repositoryId, Authentication authentication) {
		return healthAnalysisService.findForUser(repositoryId, authentication.getName());
	}
}