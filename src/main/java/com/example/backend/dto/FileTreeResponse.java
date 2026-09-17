package com.example.backend.dto;

public record FileTreeResponse(Long id, String path, String name, String extension,
		String language, long size) {
}