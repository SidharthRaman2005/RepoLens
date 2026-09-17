package com.example.backend.dto;

public record FileMetadata(Long id, String path, String name, String extension,
		String language, long size) { }