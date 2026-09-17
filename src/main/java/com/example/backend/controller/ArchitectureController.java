package com.example.backend.controller;

import com.example.backend.dto.ArchitectureResponse;
import com.example.backend.service.ArchitectureAnalysisService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/architecture")
public class ArchitectureController {

	private final ArchitectureAnalysisService analysisService;

	public ArchitectureController(ArchitectureAnalysisService analysisService) {
		this.analysisService = analysisService;
	}

	@GetMapping
	public ArchitectureResponse architecture(@PathVariable Long repositoryId, Authentication authentication) {
		return analysisService.findForUser(repositoryId, authentication.getName());
	}
}