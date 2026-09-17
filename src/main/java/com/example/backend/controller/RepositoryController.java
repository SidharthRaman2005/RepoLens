package com.example.backend.controller;

import com.example.backend.dto.RepositoryAnalyzeRequest;
import com.example.backend.dto.RepositoryResponse;
import com.example.backend.service.RepositoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

	private final RepositoryService repositoryService;

	public RepositoryController(RepositoryService repositoryService) {
		this.repositoryService = repositoryService;
	}

	@PostMapping("/analyze")
	public ResponseEntity<RepositoryResponse> analyze(
			@Valid @RequestBody RepositoryAnalyzeRequest request,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(repositoryService.analyze(request.url(), authentication.getName()));
	}

	@GetMapping
	public List<RepositoryResponse> findAll(Authentication authentication) {
		return repositoryService.findAllForUser(authentication.getName());
	}

	@GetMapping("/{id}")
	public RepositoryResponse findById(@PathVariable Long id, Authentication authentication) {
		return repositoryService.findForUser(id, authentication.getName());
	}
}