package com.example.backend.controller;

import com.example.backend.dto.CodeEntityResponse;
import com.example.backend.dto.DashboardResponse;
import com.example.backend.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories/{repositoryId}")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/dashboard")
	public DashboardResponse dashboard(@PathVariable Long repositoryId, Authentication authentication) {
		return dashboardService.dashboard(repositoryId, authentication.getName());
	}

	@GetMapping("/entities")
	public List<CodeEntityResponse> entities(@PathVariable Long repositoryId,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String file,
			@RequestParam(required = false) String name,
			Authentication authentication) {
		return dashboardService.entities(repositoryId, type, file, name, authentication.getName());
	}
}