package com.example.backend.controller;

import com.example.backend.dto.FileResponse;
import com.example.backend.dto.FileTreeResponse;
import com.example.backend.service.FileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/files")
public class FileController {

	private final FileService fileService;

	public FileController(FileService fileService) {
		this.fileService = fileService;
	}

	@GetMapping
	public List<FileResponse> findAll(@PathVariable Long repositoryId, Authentication authentication) {
		return fileService.findAll(repositoryId, authentication.getName());
	}

	@GetMapping("/{fileId}")
	public FileResponse findById(@PathVariable Long repositoryId, @PathVariable Long fileId,
			Authentication authentication) {
		return fileService.findById(repositoryId, fileId, authentication.getName());
	}

	@GetMapping("/tree")
	public List<FileTreeResponse> tree(@PathVariable Long repositoryId, Authentication authentication) {
		return fileService.tree(repositoryId, authentication.getName());
	}
}