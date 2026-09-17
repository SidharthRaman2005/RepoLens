package com.example.backend.github.dto;

public record GitHubRepositoryResponse(
		GitHubOwnerResponse owner,
		String name,
		String html_url,
		String description,
		String default_branch,
		long stargazers_count,
		long forks_count,
		String language) {
}