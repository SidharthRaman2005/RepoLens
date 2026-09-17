package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepositoryAnalyzeRequest(
		@NotBlank(message = "Repository URL is required")
		@Size(max = 2048, message = "Repository URL must not exceed 2048 characters")
		String url) {
}