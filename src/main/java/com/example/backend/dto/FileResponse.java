package com.example.backend.dto;

public record FileResponse(
		Long id,
		Long repositoryId,
		String path,
		String name,
		String extension,
		String language,
		long size,
		String content) {
}